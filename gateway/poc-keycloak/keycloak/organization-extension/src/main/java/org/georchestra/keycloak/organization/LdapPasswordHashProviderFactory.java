package org.georchestra.keycloak.organization;

import org.keycloak.Config;
import org.keycloak.credential.hash.PasswordHashProvider;
import org.keycloak.credential.hash.PasswordHashProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

/** Password hash algorithm "georchestra-ldap", see {@link LdapPasswordHashProvider} */
public class LdapPasswordHashProviderFactory implements PasswordHashProviderFactory {

    private static final LdapPasswordHashProvider PROVIDER = new LdapPasswordHashProvider();

    @Override
    public PasswordHashProvider create(KeycloakSession session) {
        return PROVIDER;
    }

    @Override
    public void init(Config.Scope config) {
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }

    @Override
    public String getId() {
        return LdapPasswordHashProvider.ID;
    }
}
