package cn.tolim.pudans.iam.synchronizer.provider;

import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceProvider;
import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderDefinition;
import cn.tolim.pudans.iam.identitysource.feishu.FeiShuConfig;
import cn.tolim.pudans.iam.identitysource.feishu.FeiShuConfigValidator;
import cn.tolim.pudans.iam.identitysource.feishu.FieShuIdentitySource;
import cn.tolim.pudans.iam.identitysource.feishu.client.FeiShuClient;


public class FeiShuIdentitySourceProvider implements IdentitySourceProviderDefinition {

    // Root department external ID used by Feishu
    public static final String ROOT_EXTERNAL_ID = "0";

    private final FeiShuConfigValidator configValidator = new FeiShuConfigValidator();

    @Override
    public String getCode() {
        return IdentitySourceProvider.FEISHU.getCode();
    }

    @Override
    public String getName() {
        return IdentitySourceProvider.FEISHU.getName();
    }

    @Override
    public Class<? extends IdentitySourceConfig> getConfigType() {
        return FeiShuConfig.class;
    }

    @Override
    public IdentitySourceConfigValidator<? extends IdentitySourceConfig> getConfigValidator() {
        return configValidator;
    }

    @Override
    public IdentitySourceClient createClient(IdentitySourceConfig config) {
        return new FeiShuClient((FeiShuConfig) config);
    }

    @Override
    public Class<? extends IdentitySource<? extends IdentitySourceConfig>> getIdentitySourceType() {
        return FieShuIdentitySource.class;
    }

    @Override
    public String getRootExternalId(IdentitySourceConfig config) {
        return ROOT_EXTERNAL_ID;
    }

    @Override
    public String getDataOriginType() {
        return IdentitySourceProvider.FEISHU.getCode();
    }

    @Override
    public String getDataOriginName() {
        return "飞书导入";
    }

    @Override
    public boolean isConfigRequired() {
        return false;
    }
}