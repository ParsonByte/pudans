package cn.tolim.pudans.iam.identitysource.scim;

import cn.tolim.pudans.iam.identitysource.scim.client.ScimClient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class ScimIdentitySourceProviderTests {

    private final ScimIdentitySourceProvider provider = new ScimIdentitySourceProvider();

    @Test
    void shouldExposeProviderIdentity() {
        assertEquals("scim", provider.getCode());
        assertEquals(ScimIdentitySourceProvider.PROVIDER_CODE, provider.getCode());
        assertEquals("SCIM", provider.getName());
        assertEquals(ScimConfig.class, provider.getConfigType());
        assertEquals(ScimIdentitySource.class, provider.getIdentitySourceType());
        assertEquals("scim", provider.getDataOriginType());
        assertEquals("SCIM导入", provider.getDataOriginName());
        assertNotNull(provider.getConfigValidator());
        assertInstanceOf(ScimConfigValidator.class, provider.getConfigValidator());
        assertTrue(provider.isConfigRequired());
    }

    @Test
    void shouldCreateScimClient() {
        ScimConfig config = validConfig();

        assertInstanceOf(ScimClient.class, provider.createClient(config));
    }

    @Test
    void shouldUseConfiguredRootDepartment() {
        ScimConfig config = validConfig();
        config.setRootDeptId("scim-directory-root");

        assertEquals("scim-directory-root", provider.getRootExternalId(config));
    }

    @Test
    void shouldFallBackToDefaultRootDepartment() {
        ScimConfig config = validConfig();

        assertEquals(ScimConstants.DEFAULT_ROOT_DEPT_ID, provider.getRootExternalId(config));
    }

    private ScimConfig validConfig() {
        ScimConfig config = new ScimConfig();
        config.setBaseUrl("https://scim.example.com/scim/v2");
        return config;
    }
}