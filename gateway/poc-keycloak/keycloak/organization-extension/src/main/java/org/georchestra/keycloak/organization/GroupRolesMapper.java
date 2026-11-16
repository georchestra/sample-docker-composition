package org.georchestra.keycloak.organization;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.GroupModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.AbstractOIDCProtocolMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAccessTokenMapper;
import org.keycloak.protocol.oidc.mappers.OIDCAttributeMapperHelper;
import org.keycloak.protocol.oidc.mappers.OIDCIDTokenMapper;
import org.keycloak.protocol.oidc.mappers.UserInfoTokenMapper;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

/**
 * Protocol mapper "geOrchestra group roles": one role per group of the user, named after the
 * group code, e.g. {@code ROLE_GRP_FINANCE} for a member of the group whose code ({@code cn}
 * attribute) is {@code FINANCE}.
 * <p>
 * The gateway adds these roles to {@code sec-roles} (claim listed in the OIDC roles json paths),
 * GeoServer takes its roles from that header, and datafeeder writes the same role names in the
 * layer rules: a group is the unit of rights for both data and metadata.
 * <p>
 * Groups without a code are ignored; a member of a subgroup also gets the role of the parent
 * groups (e.g. {@code /Finance/editors} gives {@code ROLE_GRP_FINANCE}).
 */
public class GroupRolesMapper extends AbstractOIDCProtocolMapper
        implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    public static final String PROVIDER_ID = "georchestra-group-roles";

    static final String CODE_ATTRIBUTE = "code.attribute";
    static final String ROLE_PREFIX = "role.prefix";

    private static final List<ProviderConfigProperty> CONFIG = new ArrayList<>();

    static {
        ProviderConfigProperty code = new ProviderConfigProperty();
        code.setName(CODE_ATTRIBUTE);
        code.setLabel("Group code attribute");
        code.setHelpText("Group attribute holding the group code");
        code.setType(ProviderConfigProperty.STRING_TYPE);
        code.setDefaultValue("cn");
        CONFIG.add(code);

        ProviderConfigProperty prefix = new ProviderConfigProperty();
        prefix.setName(ROLE_PREFIX);
        prefix.setLabel("Role prefix");
        prefix.setHelpText("Prefix of the role names, followed by the group code");
        prefix.setType(ProviderConfigProperty.STRING_TYPE);
        prefix.setDefaultValue("ROLE_GRP_");
        CONFIG.add(prefix);

        OIDCAttributeMapperHelper.addTokenClaimNameConfig(CONFIG);
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(CONFIG, GroupRolesMapper.class);
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "geOrchestra group roles";
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getHelpText() {
        return "One role per group of the user: prefix + group code (e.g. ROLE_GRP_FINANCE)";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return CONFIG;
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mapper, UserSessionModel userSession,
            KeycloakSession session, ClientSessionContext clientSessionCtx) {
        String codeAttribute = mapper.getConfig().getOrDefault(CODE_ATTRIBUTE, "cn");
        String prefix = mapper.getConfig().getOrDefault(ROLE_PREFIX, "ROLE_GRP_");
        Set<String> roles = new TreeSet<>();
        userSession.getUser().getGroupsStream().forEach(group -> {
            for (GroupModel g = group; g != null; g = g.getParent()) {
                String code = g.getFirstAttribute(codeAttribute);
                if (code != null && !code.isBlank()) {
                    roles.add(prefix + code.trim().toUpperCase());
                }
            }
        });
        OIDCAttributeMapperHelper.mapClaim(token, mapper, new ArrayList<>(roles));
    }
}
