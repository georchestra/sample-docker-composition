package org.georchestra.keycloak.organization;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.events.admin.OperationType;
import org.keycloak.events.admin.ResourceType;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;

/**
 * Reacts to:
 * <ul>
 * <li>a user created or updated by an admin: the organization groups follow the attribute
 * <li>a user added to a group by an admin: if it is an organization, it becomes the user's
 * only organization and the attribute follows
 * <li>a user removed from a group by an admin, a login (LDAP changes, ProConnect SIRET
 * mappers), a registration: the attribute follows the groups
 * <li>a group created or updated by an admin: an organization gets its code and is written to
 * LDAP, the options of the organization dropdown follow
 * </ul>
 * Deleted organizations are handled by {@link OrganizationEventListenerProviderFactory}.
 * Changes are applied after the transaction of the event is committed, in a new one.
 */
public class OrganizationEventListenerProvider implements EventListenerProvider {

    private static final Logger log = Logger.getLogger(OrganizationEventListenerProvider.class);

    private final KeycloakSession session;
    private final OrganizationSync.Config config;

    OrganizationEventListenerProvider(KeycloakSession session, OrganizationSync.Config config) {
        this.session = session;
        this.config = config;
    }

    @Override
    public void onEvent(Event event) {
        if (event.getUserId() == null) {
            return;
        }
        if (event.getType() == EventType.LOGIN || event.getType() == EventType.REGISTER) {
            afterCommit(session, config, event.getRealmId(), null, event.getUserId(),
                    (sync, user) -> sync.groupsToAttribute(user));
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        String[] path = event.getResourcePath() == null ? new String[0] : event.getResourcePath().split("/");
        String realmId = event.getRealmId();
        if (event.getResourceType() == ResourceType.GROUP) {
            if (event.getOperationType() == OperationType.DELETE) {
                // LDAP entries: see OrganizationEventListenerProviderFactory; the options follow
                afterCommit(session, config, realmId, OrganizationSync::updateProfileOptions, null, null);
                return;
            }
            String last = path.length >= 2 ? path[path.length - 1] : null;
            String groupId = last == null || "children".equals(last) ? null : last;
            afterCommit(session, config, realmId, sync -> {
                if (groupId != null) {
                    sync.organizationSaved(groupId);
                }
                // groups without code yet (e.g. created as a child group: the event has no id)
                sync.groupsWithoutCode().forEach(sync::organizationSaved);
                sync.updateProfileOptions();
            }, null, null);
            return;
        }
        if (event.getResourceType() == ResourceType.USER && path.length == 2 && "users".equals(path[0])
                && (event.getOperationType() == OperationType.UPDATE
                        || event.getOperationType() == OperationType.CREATE)) {
            afterCommit(session, config, realmId, null, path[1], (sync, user) -> sync.attributeToGroups(user));
        } else if (event.getResourceType() == ResourceType.GROUP_MEMBERSHIP && path.length == 4
                && "users".equals(path[0]) && "groups".equals(path[2])) {
            String groupId = path[3];
            if (event.getOperationType() == OperationType.CREATE) {
                afterCommit(session, config, realmId, null, path[1], (sync, user) -> {
                    GroupModel group = user.getGroupsStream().filter(g -> groupId.equals(g.getId())).findFirst()
                            .orElse(null);
                    if (group != null) {
                        sync.joined(user, group);
                    }
                });
            } else if (event.getOperationType() == OperationType.DELETE) {
                afterCommit(session, config, realmId, null, path[1], (sync, user) -> sync.left(user, groupId));
            }
        }
    }

    /**
     * Runs an action on the realm, or on a user, once the current transaction is committed, in
     * a new transaction.
     */
    static void afterCommit(KeycloakSession session, OrganizationSync.Config config, String realmId,
            Consumer<OrganizationSync> realmAction, String userId, BiConsumer<OrganizationSync, UserModel> userAction) {
        KeycloakSessionFactory factory = session.getKeycloakSessionFactory();
        session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction() {
            @Override
            protected void commitImpl() {
                KeycloakModelUtils.runJobInTransaction(factory, s -> {
                    RealmModel realm = s.realms().getRealm(realmId);
                    if (realm == null) {
                        return;
                    }
                    s.getContext().setRealm(realm);
                    OrganizationSync sync = new OrganizationSync(s, realm, config);
                    if (realmAction != null) {
                        try {
                            realmAction.accept(sync);
                        } catch (RuntimeException e) {
                            log.errorf(e, "Unable to update the organizations of realm %s", realm.getName());
                        }
                        return;
                    }
                    UserModel user = s.users().getUserById(realm, userId);
                    if (user == null) {
                        return;
                    }
                    try {
                        userAction.accept(sync, user);
                    } catch (RuntimeException e) {
                        log.errorf(e, "Unable to synchronize the organization of user %s", user.getUsername());
                    }
                });
            }

            @Override
            protected void rollbackImpl() {
                // nothing was done
            }
        });
    }

    @Override
    public void close() {
    }
}
