package cn.tolim.pudans.iam.synchronizer.provider;

import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConfig;
import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkConfigValidator;
import cn.tolim.pudans.iam.identitysource.dingtalk.DingTalkIdentitySource;
import cn.tolim.pudans.iam.identitysource.feishu.FeiShuConfig;
import cn.tolim.pudans.iam.identitysource.feishu.FeiShuConfigValidator;
import cn.tolim.pudans.iam.identitysource.feishu.FieShuIdentitySource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;


class LegacyIdentitySourceProviderTests {

    @Test
    void shouldExposeDingTalkProviderIdentity() {
        DingTalkIdentitySourceProvider provider = new DingTalkIdentitySourceProvider();

        assertEquals("dingtalk", provider.getCode());
        assertEquals("钉钉", provider.getName());
        assertEquals(DingTalkConfig.class, provider.getConfigType());
        assertEquals(DingTalkIdentitySource.class, provider.getIdentitySourceType());
        assertEquals("1", provider.getRootExternalId(new DingTalkConfig()));
        assertEquals("dingtalk", provider.getDataOriginType());
        assertEquals("钉钉导入", provider.getDataOriginName());
        assertInstanceOf(DingTalkConfigValidator.class, provider.getConfigValidator());
        // The legacy root department ID is a constant, so configuration is not needed
        assertFalse(provider.isConfigRequired());
    }

    @Test
    void shouldExposeFeiShuProviderIdentity() {
        FeiShuIdentitySourceProvider provider = new FeiShuIdentitySourceProvider();

        assertEquals("feishu", provider.getCode());
        assertEquals("飞书", provider.getName());
        assertEquals(FeiShuConfig.class, provider.getConfigType());
        assertEquals(FieShuIdentitySource.class, provider.getIdentitySourceType());
        assertEquals("0", provider.getRootExternalId(new FeiShuConfig()));
        assertEquals("feishu", provider.getDataOriginType());
        assertEquals("飞书导入", provider.getDataOriginName());
        assertInstanceOf(FeiShuConfigValidator.class, provider.getConfigValidator());
        // The legacy root department ID is a constant, so configuration is not needed
        assertFalse(provider.isConfigRequired());
    }
}