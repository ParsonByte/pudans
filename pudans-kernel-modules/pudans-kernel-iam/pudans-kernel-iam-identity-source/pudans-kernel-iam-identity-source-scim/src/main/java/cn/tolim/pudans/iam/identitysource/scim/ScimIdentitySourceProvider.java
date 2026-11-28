package cn.tolim.pudans.iam.identitysource.scim;

import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderDefinition;
import cn.tolim.pudans.iam.identitysource.scim.client.ScimClient;


public class ScimIdentitySourceProvider implements IdentitySourceProviderDefinition {

    // Provider code persisted in the identity source record
    public static final String PROVIDER_CODE = "scim";
    // Provider display name
    public static final String PROVIDER_NAME = "SCIM";

    private final ScimConfigValidator configValidator = new ScimConfigValidator();

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
        return ScimConfig.class;
    }

    @Override
    public IdentitySourceConfigValidator<? extends IdentitySourceConfig> getConfigValidator() {
        return configValidator;
    }

    @Override
    public IdentitySourceClient createClient(IdentitySourceConfig config) {
        return new ScimClient((ScimConfig) config);
    }

    @Override
    public Class<? extends IdentitySource<? extends IdentitySourceConfig>> getIdentitySourceType() {
        return ScimIdentitySource.class;
    }

    @Override
    public String getRootExternalId(IdentitySourceConfig config) {
        // The synthetic root department ID is configurable for SCIM
        return ((ScimConfig) config).getEffectiveRootDeptId();
    }

    @Override
    public String getDataOriginType() {
        return PROVIDER_CODE;
    }

    @Override
    public String getDataOriginName() {
        return "SCIM导入";
    }
}