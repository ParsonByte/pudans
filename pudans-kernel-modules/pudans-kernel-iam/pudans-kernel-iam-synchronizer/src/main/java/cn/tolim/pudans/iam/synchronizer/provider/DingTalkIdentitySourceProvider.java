package cn.tolim.pudans.iam.synchronizer.provider;

import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceProvider;
import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderDefinition;
import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConfig;
import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConfigValidator;
import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkIdentitySource;
import cn.tolim.pudans.iam.identitysource.dingtalk.client.DingTalkClient;


public class DingTalkIdentitySourceProvider implements IdentitySourceProviderDefinition {

    // Root department external ID used by DingTalk
    public static final String ROOT_EXTERNAL_ID = "1";

    private final DingTalkConfigValidator configValidator = new DingTalkConfigValidator();

    @Override
    public String getCode() {
        return IdentitySourceProvider.DINGTALK.getCode();
    }

    @Override
    public String getName() {
        return IdentitySourceProvider.DINGTALK.getName();
    }

    @Override
    public Class<? extends IdentitySourceConfig> getConfigType() {
        return DingTalkConfig.class;
    }

    @Override
    public IdentitySourceConfigValidator<? extends IdentitySourceConfig> getConfigValidator() {
        return configValidator;
    }

    @Override
    public IdentitySourceClient createClient(IdentitySourceConfig config) {
        return new DingTalkClient((DingTalkConfig) config);
    }

    @Override
    public Class<? extends IdentitySource<? extends IdentitySourceConfig>> getIdentitySourceType() {
        return DingTalkIdentitySource.class;
    }

    @Override
    public String getRootExternalId(IdentitySourceConfig config) {
        return ROOT_EXTERNAL_ID;
    }

    @Override
    public String getDataOriginType() {
        return IdentitySourceProvider.DINGTALK.getCode();
    }

    @Override
    public String getDataOriginName() {
        return "钉钉导入";
    }

    @Override
    public boolean isConfigRequired() {
        return false;
    }
}