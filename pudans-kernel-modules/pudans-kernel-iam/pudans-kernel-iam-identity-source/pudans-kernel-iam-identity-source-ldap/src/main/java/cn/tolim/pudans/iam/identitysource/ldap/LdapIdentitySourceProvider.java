package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderDefinition;


public class LdapIdentitySourceProvider implements IdentitySourceProviderDefinition {

    // Provider code persisted in the identity source record
    public static final String PROVIDER_CODE = "ldap";
    // Provider display name
    public static final String PROVIDER_NAME = "LDAP";

    private final LdapConfigValidator configValidator = new LdapConfigValidator();

    @Override
    public String getCode() {
        return PROVIDER_CODE;
    }

    @Override
    public String getName() {
        return PROVIDER_NAME;
    }

    @Override
    public Class<? extends IdentitySourceConfig> getConfigType() {
        return LdapConfig.class;
    }

    @Override
    public IdentitySourceConfigValidator<? extends IdentitySourceConfig> getConfigValidator() {
        return configValidator;
    }

    @Override
    public IdentitySourceClient createClient(IdentitySourceConfig config) {
        return new LdapClient((LdapConfig) config);
    }

    @Override
    public Class<? extends IdentitySource<? extends IdentitySourceConfig>> getIdentitySourceType() {
        return LdapIdentitySource.class;
    }

    @Override
    public String getRootExternalId(IdentitySourceConfig config) {
        // The synthetic root department ID is configurable for LDAP
        return ((LdapConfig) config).getEffectiveRootDeptId();
    }

    @Override
    public String getDataOriginType() {
        return PROVIDER_CODE;
    }

    @Override
    public String getDataOriginName() {
        return "LDAP导入";
    }
}