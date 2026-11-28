package cn.tolim.pudans.iam.identitysource.ldap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class LdapIdentitySourceProviderTests {

    private final LdapIdentitySourceProvider provider = new LdapIdentitySourceProvider();

    @Test
    void shouldExposeProviderIdentity() {
        assertEquals("ldap", provider.getCode());
        assertEquals(LdapIdentitySourceProvider.PROVIDER_CODE, provider.getCode());
        assertEquals("LDAP", provider.getName());
        assertEquals(LdapConfig.class, provider.getConfigType());
        assertEquals(LdapIdentitySource.class, provider.getIdentitySourceType());
        assertEquals("ldap", provider.getDataOriginType());
        assertEquals("LDAP导入", provider.getDataOriginName());
        assertNotNull(provider.getConfigValidator());
        assertInstanceOf(LdapConfigValidator.class, provider.getConfigValidator());
        assertTrue(provider.isConfigRequired());
    }

    @Test
    void shouldCreateLdapClient() {
        LdapConfig config = validConfig();

        assertInstanceOf(LdapClient.class, provider.createClient(config));
    }

    @Test
    void shouldUseConfiguredRootDepartment() {
        LdapConfig config = validConfig();
        config.setRootDeptId("directory-root");

        assertEquals("directory-root", provider.getRootExternalId(config));
    }

    @Test
    void shouldFallBackToDefaultRootDepartment() {
        LdapConfig config = validConfig();

        assertEquals(LdapConstants.DEFAULT_ROOT_DEPT_ID, provider.getRootExternalId(config));
    }

    private LdapConfig validConfig() {
        LdapConfig config = new LdapConfig();
        config.setServerUrl("ldap://127.0.0.1:389");
        config.setBaseDn("dc=example,dc=com");
        return config;
    }
}