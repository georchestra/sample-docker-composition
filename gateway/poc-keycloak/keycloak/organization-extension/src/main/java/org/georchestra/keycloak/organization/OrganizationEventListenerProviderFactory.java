package org.georchestra.keycloak.organization;

import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;

/**
 * Event listener "georchestra-organization", enabled per realm (Realm settings, Events, Event
 * listeners).
 * <p>
 * Options ({@code --spi-events-listener--georchestra-organization--<option>=<value>}):
 * <ul>
 * <li>{@code attribute}: user attribute holding the organization code (default
 * {@code organization})
 * <li>{@code code-attribute}: group attribute holding the organization code (default
 * {@code cn}, the LDAP attribute, so that the LDAP group mapper fills it for existing
 * organizations)
 * <li>{@code org-group-path}: path of the group whose children are the organizations (default
 * empty: top-level groups)
 * <li>{@code flag-attribute}: group attribute flagging an organization, value {@code true}
 * (default {@code isOrganization}, "Est un organisme")
 * <li>{@code ldap-write}: write organizations to the federated LDAP (default {@code true})
 * </ul>
 */
public class OrganizationEventListenerProviderFactory implements EventListenerProviderFactory {

    public static final String ID = "georchestra-organization";

    private final OrganizationSync.Config config = new OrganizationSync.Config();

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new OrganizationEventListenerProvider(session, config);
    }

    @Override
    public void init(Config.Scope scope) {
        config.attribute = scope.get("attribute", config.attribute);
        config.codeAttribute = scope.get("code-attribute", config.codeAttribute);
        config.ldapWrite = scope.getBoolean("ldap-write", config.ldapWrite);
        config.flagAttribute = scope.get("flag-attribute", config.flagAttribute);
        String path = scope.get("org-group-path", config.orgGroupPath).trim();
        while (path.startsWith("/")) {
            path = path.substring(1);
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        config.orgGroupPath = path;
    }

    /**
     * A deleted group no longer exists when the admin event is sent: its code and members are
     * read before the removal; once it is committed, its LDAP entries are deleted and its
     * members' organization attribute follows.
     */
    @Override
    public void postInit(KeycloakSessionFactory factory) {
        factory.register(event -> {
            if (!(event instanceof GroupModel.GroupRemovedEvent)) {
                return;
            }
            GroupModel.GroupRemovedEvent removed = (GroupModel.GroupRemovedEvent) event;
            RealmModel realm = removed.getRealm();
            KeycloakSession session = removed.getKeycloakSession();
            if (realm == null || session == null || realm.getEventsListenersStream().noneMatch(ID::equals)) {
                return;
            }
            OrganizationSync sync = new OrganizationSync(session, realm, config);
            GroupModel group = removed.getGroup();
            if (!sync.isOrganization(group)) {
                return;
            }
            String code = group.getFirstAttribute(config.codeAttribute);
            java.util.List<String> members = sync.memberIds(group);
            OrganizationEventListenerProvider.afterCommit(session, config, realm.getId(), s -> {
                s.organizationDeleted(code);
                s.refreshUsers(members);
            }, null, null);
        });
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return ID;
    }
}
