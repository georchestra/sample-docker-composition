package org.georchestra.keycloak.organization;

import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import javax.naming.Context;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.ModificationItem;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;
import javax.naming.ldap.Rdn;

import org.jboss.logging.Logger;
import org.keycloak.component.ComponentModel;
import org.keycloak.models.RealmModel;

/**
 * Writes geOrchestra organizations to the LDAP federated in the realm, with the connection of
 * its "ldap" user storage provider and the base DN of its group mapper. An organization is two
 * entries under that base DN:
 * <ul>
 * <li>{@code cn=<CODE>} ({@code groupOfMembers}): the members (written by Keycloak's group
 * mapper), {@code o} = the organization name
 * <li>{@code o=<CODE>} ({@code georchestraOrg}): the organization details
 * </ul>
 */
class OrganizationLdap {

    private static final Logger log = Logger.getLogger(OrganizationLdap.class);

    /** Provider type of the user federation components */
    private static final String USER_STORAGE_PROVIDER_TYPE = "org.keycloak.storage.UserStorageProvider";

    /** Keycloak group attribute -> LDAP attribute of the {@code o=<CODE>} entry */
    static final Map<String, String> DETAILS = new LinkedHashMap<>();
    static {
        DETAILS.put("siret", "orgUniqueId");
        DETAILS.put("businessCategory", "businessCategory");
        DETAILS.put("description", "description");
        DETAILS.put("mail", "mail");
    }

    private final Hashtable<String, String> env = new Hashtable<>();
    private final String orgsDn;

    private OrganizationLdap(ComponentModel ldap, String orgsDn) {
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
        env.put(Context.PROVIDER_URL, ldap.get("connectionUrl"));
        env.put(Context.SECURITY_AUTHENTICATION, "simple");
        env.put(Context.SECURITY_PRINCIPAL, ldap.get("bindDn"));
        env.put(Context.SECURITY_CREDENTIALS, ldap.get("bindCredential"));
        env.put("com.sun.jndi.ldap.connect.timeout", "5000");
        this.orgsDn = orgsDn;
    }

    /** The LDAP of the realm, if it federates one with a group mapper */
    static Optional<OrganizationLdap> of(RealmModel realm) {
        return realm.getComponentsStream(realm.getId(), USER_STORAGE_PROVIDER_TYPE)
                .filter(c -> "ldap".equals(c.getProviderId()))
                .flatMap(ldap -> realm.getComponentsStream(ldap.getId())
                        .filter(m -> "group-ldap-mapper".equals(m.getProviderId()))
                        .map(m -> new OrganizationLdap(ldap, m.get("groups.dn"))))
                .findFirst();
    }

    /** Creates or updates the two entries of an organization */
    void save(String code, String name, Map<String, String> details) throws NamingException {
        LdapContext ctx = new InitialLdapContext(env, null);
        try {
            String groupDn = dn("cn", code);
            if (exists(ctx, groupDn)) {
                ctx.modifyAttributes(groupDn, new ModificationItem[] {
                        new ModificationItem(DirContext.REPLACE_ATTRIBUTE, new BasicAttribute("o", name)) });
            } else {
                BasicAttributes attrs = new BasicAttributes(true);
                attrs.put(objectClasses("top", "groupOfMembers"));
                attrs.put("cn", code);
                attrs.put("o", name);
                ctx.createSubcontext(groupDn, attrs).close();
                log.infof("Created LDAP organization group %s", groupDn);
            }

            String orgDn = dn("o", code);
            if (!exists(ctx, orgDn)) {
                BasicAttributes attrs = new BasicAttributes(true);
                attrs.put(objectClasses("top", "organization", "georchestraOrg", "extensibleObject"));
                attrs.put("o", code);
                ctx.createSubcontext(orgDn, attrs).close();
                log.infof("Created LDAP organization %s", orgDn);
            }
            ModificationItem[] details_ = details.entrySet().stream()
                    .filter(e -> DETAILS.containsKey(e.getKey()))
                    .map(e -> new ModificationItem(DirContext.REPLACE_ATTRIBUTE,
                            new BasicAttribute(DETAILS.get(e.getKey()), e.getValue())))
                    .toArray(ModificationItem[]::new);
            if (details_.length > 0) {
                ctx.modifyAttributes(orgDn, details_);
            }
        } finally {
            ctx.close();
        }
    }

    /** Adds a member (user DN) to an organization */
    void addMember(String code, String memberDn) throws NamingException {
        modifyMember(code, memberDn, DirContext.ADD_ATTRIBUTE);
    }

    /** Removes a member (user DN) from an organization */
    void removeMember(String code, String memberDn) throws NamingException {
        modifyMember(code, memberDn, DirContext.REMOVE_ATTRIBUTE);
    }

    private void modifyMember(String code, String memberDn, int operation) throws NamingException {
        LdapContext ctx = new InitialLdapContext(env, null);
        try {
            ctx.modifyAttributes(dn("cn", code), new ModificationItem[] {
                    new ModificationItem(operation, new BasicAttribute("member", memberDn)) });
        } catch (javax.naming.directory.AttributeInUseException
                | javax.naming.directory.NoSuchAttributeException e) {
            // already a member / not a member
        } finally {
            ctx.close();
        }
    }

    /**
     * Deletes the two entries of an organization.
     *
     * @return whether an entry was deleted
     */
    boolean delete(String code) throws NamingException {
        boolean deleted = false;
        LdapContext ctx = new InitialLdapContext(env, null);
        try {
            for (String dn : new String[] { dn("cn", code), dn("o", code) }) {
                if (exists(ctx, dn)) {
                    ctx.destroySubcontext(dn);
                    deleted = true;
                    log.infof("Deleted LDAP entry %s", dn);
                }
            }
        } finally {
            ctx.close();
        }
        return deleted;
    }

    /** The details of the {@code o=<CODE>} entry, as Keycloak group attributes */
    Map<String, String> details(String code) throws NamingException {
        Map<String, String> details = new LinkedHashMap<>();
        LdapContext ctx = new InitialLdapContext(env, null);
        try {
            javax.naming.directory.Attributes attrs = ctx.getAttributes(dn("o", code),
                    DETAILS.values().toArray(new String[0]));
            for (Map.Entry<String, String> e : DETAILS.entrySet()) {
                Attribute attr = attrs.get(e.getValue());
                if (attr != null && attr.get() != null) {
                    details.put(e.getKey(), attr.get().toString());
                }
            }
        } catch (NameNotFoundException e) {
            // no details entry
        } finally {
            ctx.close();
        }
        return details;
    }

    private String dn(String attribute, String code) {
        return attribute + "=" + Rdn.escapeValue(code) + "," + orgsDn;
    }

    private static boolean exists(LdapContext ctx, String dn) throws NamingException {
        try {
            ctx.getAttributes(dn, new String[] { "objectClass" });
            return true;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    private static Attribute objectClasses(String... values) {
        BasicAttribute attr = new BasicAttribute("objectClass");
        for (String value : values) {
            attr.add(value);
        }
        return attr;
    }
}
