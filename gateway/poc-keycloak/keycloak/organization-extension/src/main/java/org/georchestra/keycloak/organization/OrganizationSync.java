package org.georchestra.keycloak.organization;

import java.text.Normalizer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jboss.logging.Logger;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.utils.KeycloakModelUtils;
import org.keycloak.representations.userprofile.config.UPAttribute;
import org.keycloak.representations.userprofile.config.UPAttributePermissions;
import org.keycloak.representations.userprofile.config.UPConfig;
import org.keycloak.userprofile.UserProfileProvider;

/**
 * geOrchestra organizations in Keycloak.
 * <p>
 * An organization is a group (a top-level group, or a child of the configured parent group)
 * flagged as such ({@code isOrganization=true} group attribute, "Est un organisme"); the other
 * groups are plain groups a user can belong to in any number:
 * <ul>
 * <li>its <b>name</b> is the organization name, the only one people type and read
 * <li>its <b>code</b>, the technical identifier used by LDAP, GeoServer, GeoNetwork, datafeeder
 * and the {@code sec-org} header, is a group attribute, generated from the name when the group
 * is created and never changed afterwards
 * </ul>
 * A user belongs to at most one organization; its code is also the value of the user's
 * organization attribute (a dropdown in the admin console), kept in sync with the membership.
 */
class OrganizationSync {

    private static final Logger log = Logger.getLogger(OrganizationSync.class);

    private static final int CODE_MAX_LENGTH = 64;

    /** Label of the organization dropdown, a message of the georchestra themes */
    static final String DISPLAY_NAME = "${organization}";

    private final KeycloakSession session;
    private final RealmModel realm;
    private final String attribute;
    private final String codeAttribute;
    private final String orgGroupPath;
    private final String flagAttribute;
    private final boolean ldapWrite;

    OrganizationSync(KeycloakSession session, RealmModel realm, Config config) {
        this.session = session;
        this.realm = realm;
        this.attribute = config.attribute;
        this.codeAttribute = config.codeAttribute;
        this.orgGroupPath = config.orgGroupPath;
        this.flagAttribute = config.flagAttribute;
        this.ldapWrite = config.ldapWrite;
    }

    static class Config {
        String attribute = "organization";
        String codeAttribute = "cn";
        String orgGroupPath = "";
        String flagAttribute = "isOrganization";
        boolean ldapWrite = true;
    }

    // ------------------------------------------------------------ organizations

    Stream<GroupModel> organizations() {
        return candidates().filter(this::flagged);
    }

    /** Ids of the candidate groups that have no code yet */
    List<String> groupsWithoutCode() {
        return candidates().filter(g -> {
            String code = g.getFirstAttribute(codeAttribute);
            return code == null || code.isBlank();
        }).map(GroupModel::getId).collect(Collectors.toList());
    }

    /** Groups that may be organizations: top-level groups, or children of the parent group */
    private Stream<GroupModel> candidates() {
        if (orgGroupPath.isEmpty()) {
            return realm.getTopLevelGroupsStream();
        }
        GroupModel parent = KeycloakModelUtils.findGroupByPath(session, realm, "/" + orgGroupPath);
        if (parent == null) {
            log.warnf("Organizations parent group /%s not found in realm %s", orgGroupPath, realm.getName());
            return Stream.empty();
        }
        return parent.getSubGroupsStream();
    }

    boolean isOrganization(GroupModel group) {
        return isCandidate(group) && flagged(group);
    }

    private boolean flagged(GroupModel group) {
        return "true".equalsIgnoreCase(group.getFirstAttribute(flagAttribute));
    }

    private boolean isCandidate(GroupModel group) {
        if (orgGroupPath.isEmpty()) {
            return group.getParentId() == null;
        }
        GroupModel parent = group.getParent();
        return parent != null && ("/" + orgGroupPath).equals(KeycloakModelUtils.buildGroupPath(parent));
    }

    /** The code of an organization; its name until a code is set */
    String code(GroupModel group) {
        String code = group.getFirstAttribute(codeAttribute);
        return code == null || code.isBlank() ? group.getName() : code;
    }

    private Optional<GroupModel> organizationByCode(String code) {
        return organizations().filter(g -> code(g).equals(code)).findFirst();
    }

    /** Code generated from a name: upper case ASCII letters, digits and underscores */
    static String toCode(String name) {
        String ascii = Normalizer.normalize(name == null ? "" : name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        String code = ascii.toUpperCase().replaceAll("[^A-Z0-9]+", "_").replaceAll("^_+|_+$", "");
        if (code.length() > CODE_MAX_LENGTH) {
            code = code.substring(0, CODE_MAX_LENGTH).replaceAll("_+$", "");
        }
        return code.isEmpty() ? "ORG" : code;
    }

    /**
     * A group was created or changed.
     * <ul>
     * <li>flagged organization: it gets a code if it has none, and is written to LDAP (name,
     * details, members)
     * <li>no flag but a code: an organization imported from LDAP, it gets flagged
     * <li>flag set to false while it has a code: it is no longer an organization, its LDAP
     * entries are deleted and its members' organization attribute follows
     * </ul>
     */
    void organizationSaved(String groupId) {
        GroupModel group = realm.getGroupById(groupId);
        if (group == null || !isCandidate(group)) {
            return;
        }
        String flag = group.getFirstAttribute(flagAttribute);
        String existingCode = group.getFirstAttribute(codeAttribute);
        boolean hasCode = existingCode != null && !existingCode.isBlank();
        if ((flag == null || flag.isBlank()) && hasCode) {
            group.setSingleAttribute(flagAttribute, "true");
            log.infof("Group '%s' (code %s, from LDAP) flagged as an organization", group.getName(), existingCode);
        } else if (!flagged(group)) {
            // a plain group: it gets a code too (GeoNetwork group, datafeeder rules...)
            if (flag == null || flag.isBlank()) {
                group.setSingleAttribute(flagAttribute, "false");
            }
            if (!hasCode) {
                generateCode(group);
            } else if (organizationDeleted(existingCode)) {
                log.infof("Group '%s' is no longer an organization", group.getName());
                members(group).forEach(this::groupsToAttribute);
            }
            return;
        }
        String code = group.getFirstAttribute(codeAttribute);
        if (code == null || code.isBlank()) {
            code = generateCode(group);
        }
        if (!ldapWrite) {
            return;
        }
        Optional<OrganizationLdap> ldap = OrganizationLdap.of(realm);
        if (ldap.isEmpty()) {
            return;
        }
        try {
            Map<String, String> details = new LinkedHashMap<>();
            Map<String, String> fromLdap = ldap.get().details(code);
            for (String key : OrganizationLdap.DETAILS.keySet()) {
                String value = group.getFirstAttribute(key);
                if (value != null && !value.isBlank()) {
                    details.put(key, value);
                } else if (fromLdap.containsKey(key)) {
                    // known in LDAP only (organization created before): shown in Keycloak too
                    group.setSingleAttribute(key, fromLdap.get(key));
                }
            }
            ldap.get().save(code, group.getName(), details);
        } catch (Exception e) {
            log.errorf(e, "Unable to write organization %s to LDAP", code);
        }
        // members (e.g. a group just flagged as an organization)
        members(group).forEach(user -> {
            ldapMember(group, user, true);
            groupsToAttribute(user);
        });
    }

    /** Member ids of a group (e.g. before it is deleted) */
    List<String> memberIds(GroupModel group) {
        return session.users().getGroupMembersStream(realm, group).map(UserModel::getId)
                .collect(Collectors.toList());
    }

    /** The organization attribute of these users follows their groups (e.g. organization deleted) */
    void refreshUsers(List<String> userIds) {
        userIds.stream().map(id -> session.users().getUserById(realm, id)).filter(Objects::nonNull)
                .forEach(this::groupsToAttribute);
    }

    /** A unique code generated from the group name, among all the candidate groups */
    private String generateCode(GroupModel group) {
        Set<String> taken = candidates().filter(g -> !g.getId().equals(group.getId())).map(this::code)
                .collect(Collectors.toSet());
        String base = toCode(group.getName());
        String code = base;
        for (int i = 2; taken.contains(code); i++) {
            code = base + "_" + i;
        }
        group.setSingleAttribute(codeAttribute, code);
        log.infof("Group '%s' gets code %s", group.getName(), code);
        return code;
    }

    /**
     * An organization was deleted (or is no longer one): its LDAP entries too.
     *
     * @return whether LDAP entries were deleted
     */
    boolean organizationDeleted(String code) {
        if (!ldapWrite || code == null || code.isBlank()) {
            return false;
        }
        return OrganizationLdap.of(realm).map(ldap -> {
            try {
                return ldap.delete(code);
            } catch (Exception e) {
                log.errorf(e, "Unable to delete organization %s from LDAP", code);
                return false;
            }
        }).orElse(false);
    }

    // ----------------------------------------------------------------- users

    /**
     * Members of a group, loaded one by one through the user cache: the users returned by
     * getGroupMembersStream bypass it, so changing them would leave stale cached users.
     */
    private List<UserModel> members(GroupModel group) {
        return session.users().getGroupMembersStream(realm, group).map(UserModel::getId)
                .collect(Collectors.toList()).stream()
                .map(id -> session.users().getUserById(realm, id))
                .filter(Objects::nonNull).collect(Collectors.toList());
    }

    private List<GroupModel> organizationsOf(UserModel user) {
        return user.getGroupsStream().filter(this::isOrganization).collect(Collectors.toList());
    }

    /**
     * The attribute changed (or may have): the organization groups follow it. An empty
     * attribute is filled from the current organization group instead, so that a user never
     * loses its organization because the attribute was not set yet.
     */
    void attributeToGroups(UserModel user) {
        String value = user.getFirstAttribute(attribute);
        List<GroupModel> current = organizationsOf(user);
        if (value == null || value.isBlank()) {
            if (current.size() == 1) {
                user.setSingleAttribute(attribute, code(current.get(0)));
            }
            return;
        }
        Optional<GroupModel> target = organizationByCode(value);
        if (target.isEmpty()) {
            log.warnf("User %s: unknown organization '%s'", user.getUsername(), value);
            return;
        }
        moveTo(user, target.get(), current);
    }

    /** The user joined a group: if it is an organization, it becomes its only organization. */
    void joined(UserModel user, GroupModel group) {
        if (!isOrganization(group)) {
            return;
        }
        moveTo(user, group, organizationsOf(user));
        ldapMember(group, user, true);
        user.setSingleAttribute(attribute, code(group));
    }

    /** The user left a group: if it is an organization, LDAP and the attribute follow. */
    void left(UserModel user, String groupId) {
        GroupModel group = realm.getGroupById(groupId);
        if (group != null && isOrganization(group)) {
            ldapMember(group, user, false);
        }
        groupsToAttribute(user);
    }

    /** Group membership changed (removal, login): the attribute follows the groups. */
    void groupsToAttribute(UserModel user) {
        List<GroupModel> current = organizationsOf(user);
        String value = user.getFirstAttribute(attribute);
        if (current.isEmpty()) {
            if (value != null) {
                user.removeAttribute(attribute);
            }
        } else if (current.stream().noneMatch(g -> code(g).equals(value))) {
            if (current.size() > 1) {
                log.warnf("User %s belongs to %d organizations, using %s", user.getUsername(), current.size(),
                        current.get(0).getName());
            }
            user.setSingleAttribute(attribute, code(current.get(0)));
        }
    }

    private void moveTo(UserModel user, GroupModel target, List<GroupModel> current) {
        for (GroupModel group : current) {
            if (!Objects.equals(group.getId(), target.getId())) {
                user.leaveGroup(group);
                ldapMember(group, user, false);
                log.infof("User %s left organization %s", user.getUsername(), group.getName());
            }
        }
        if (current.stream().noneMatch(g -> Objects.equals(g.getId(), target.getId()))) {
            user.joinGroup(target);
            ldapMember(target, user, true);
            log.infof("User %s joined organization %s", user.getUsername(), target.getName());
        }
    }

    /**
     * Membership of an organization in LDAP ({@code member} of {@code cn=<CODE>}): Keycloak's
     * group mapper only imports it, the organizations being managed in Keycloak.
     */
    private void ldapMember(GroupModel organization, UserModel user, boolean add) {
        String userDn = user.getFirstAttribute("LDAP_ENTRY_DN");
        if (!ldapWrite || userDn == null) {
            return; // not an LDAP user
        }
        OrganizationLdap.of(realm).ifPresent(ldap -> {
            try {
                if (add) {
                    ldap.addMember(code(organization), userDn);
                } else {
                    ldap.removeMember(code(organization), userDn);
                }
            } catch (Exception e) {
                log.errorf(e, "Unable to update the LDAP members of organization %s", code(organization));
            }
        });
    }

    // ------------------------------------------------------- user profile

    /**
     * The organizations changed: the options of the organization dropdown of the user profile
     * follow (value = code, label = name). The attribute is declared if missing.
     */
    void updateProfileOptions() {
        List<GroupModel> orgs = organizations()
                .sorted(Comparator.comparing(g -> g.getName().toLowerCase())).collect(Collectors.toList());
        List<String> codes = orgs.stream().map(this::code).collect(Collectors.toList());
        Map<String, Object> labels = new LinkedHashMap<>();
        for (GroupModel org : orgs) {
            labels.put(code(org), org.getName());
        }

        UserProfileProvider provider = session.getProvider(UserProfileProvider.class);
        UPConfig config = provider.getConfiguration();
        UPAttribute attr = config.getAttribute(attribute);
        if (attr == null) {
            attr = new UPAttribute(attribute);
            attr.setMultivalued(false);
        }
        // editable by the user (registration, first choice), changes are then refused by the
        // validator outside of the admin contexts
        UPAttributePermissions permissions = new UPAttributePermissions(Set.of("admin", "user"),
                Set.of("admin", "user"));
        boolean permissionsChanged = attr.getPermissions() == null
                || !permissions.getView().equals(attr.getPermissions().getView())
                || !permissions.getEdit().equals(attr.getPermissions().getEdit());
        Map<String, Object> annotations = attr.getAnnotations() == null ? new HashMap<>()
                : new HashMap<>(attr.getAnnotations());
        annotations.put("inputType", "select");
        annotations.put("inputOptionLabels", labels);
        annotations.put("inputHelperTextAfter", "${organizationHelp}");
        Map<String, Map<String, Object>> validations = attr.getValidations() == null ? new HashMap<>()
                : new HashMap<>(attr.getValidations());
        if (codes.isEmpty()) {
            validations.remove("options");
        } else {
            validations.put("options", Map.of("options", codes));
        }
        validations.put(OrganizationLockedValidator.ID, Map.of());
        if (!permissionsChanged && DISPLAY_NAME.equals(attr.getDisplayName())
                && annotations.equals(attr.getAnnotations())
                && validations.equals(attr.getValidations())) {
            return;
        }
        attr.setDisplayName(DISPLAY_NAME);
        attr.setPermissions(permissions);
        attr.setAnnotations(annotations);
        attr.setValidations(validations);
        config.addOrReplaceAttribute(attr);
        provider.setConfiguration(config);
        log.infof("Organization dropdown of realm %s updated: %s", realm.getName(), codes);
    }
}
