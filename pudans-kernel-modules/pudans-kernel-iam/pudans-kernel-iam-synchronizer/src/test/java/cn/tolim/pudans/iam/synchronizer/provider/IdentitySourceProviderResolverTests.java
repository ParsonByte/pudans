package cn.tolim.pudans.iam.synchronizer.provider;

import cn.tolim.pudans.iam.communal.entity.identitysource.IdentitySourceEntity;
import cn.tolim.pudans.iam.communal.enums.identitysource.IdentitySourceProvider;
import cn.tolim.pudans.iam.communal.exception.IdentitySourceProviderNotSupportedException;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderMetadata;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderRegistry;
import cn.tolim.pudans.iam.identitysource.ldap.LdapIdentitySourceProvider;
import cn.tolim.pudans.iam.identitysource.scim.ScimConfig;
import cn.tolim.pudans.iam.identitysource.scim.ScimIdentitySourceProvider;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;


class IdentitySourceProviderResolverTests {

    private final FakeProviderCodeReader codeReader = new FakeProviderCodeReader();

    private final IdentitySourceProviderResolver resolver = new IdentitySourceProviderResolver(
            new IdentitySourceProviderRegistry(List.of(new LdapIdentitySourceProvider(),
                    new ScimIdentitySourceProvider(), new DingTalkIdentitySourceProvider(),
                    new FeiShuIdentitySourceProvider())), codeReader);

    @Test
    void shouldResolveLdapProviderFromPersistedCode() {
        IdentitySourceEntity entity = entity("ldap-1", """
                {"serverUrl":"ldap://127.0.0.1:389","baseDn":"dc=example,dc=com",
                 "rootDeptId":"ldap-root-1","rootDeptName":"Directory"}
                """);
        codeReader.codes.put("ldap-1", "ldap");

        IdentitySourceProviderMetadata metadata = resolver.resolve(entity);

        assertEquals("ldap", metadata.providerCode());
        assertEquals("LDAP", metadata.providerName());
        assertEquals("ldap-root-1", metadata.rootExternalId());
        assertEquals("ldap", metadata.dataOriginType());
        assertEquals("LDAP导入", metadata.dataOriginName());
        assertEquals("ldap", resolver.resolveProviderCode(entity));
    }

    @Test
    void shouldResolveScimProviderFromPersistedCode() {
        IdentitySourceEntity entity = entity("scim-1", """
                {"baseUrl":"https://scim.example.com/scim/v2","rootDeptId":"scim-root-1"}
                """);
        codeReader.codes.put("scim-1", "scim");

        IdentitySourceProviderMetadata metadata = resolver.resolve(entity);

        assertEquals("scim", metadata.providerCode());
        assertEquals("SCIM", metadata.providerName());
        assertEquals("scim-root-1", metadata.rootExternalId());
        assertEquals("scim", metadata.dataOriginType());
        assertEquals("SCIM导入", metadata.dataOriginName());
    }

    @Test
    void shouldResolveLegacyProviderFromEntityEnum() {
        IdentitySourceEntity entity = entity("dingtalk-1", null);
        entity.setProvider(IdentitySourceProvider.DINGTALK);

        IdentitySourceProviderMetadata metadata = resolver.resolve(entity);

        assertEquals("dingtalk", metadata.providerCode());
        assertEquals("1", metadata.rootExternalId());
        assertEquals("dingtalk", metadata.dataOriginType());
        assertEquals(0, codeReader.calls);
    }

    @Test
    void shouldReuseCachedMetadataUntilConfigurationChanges() {
        IdentitySourceEntity entity = entity("ldap-2", """
                {"serverUrl":"ldap://127.0.0.1:389","baseDn":"dc=example,dc=com",
                 "rootDeptId":"root-a"}
                """);
        codeReader.codes.put("ldap-2", "ldap");

        assertEquals("root-a", resolver.resolve(entity).rootExternalId());
        assertEquals("root-a", resolver.resolve(entity).rootExternalId());
        assertEquals("root-a", resolver.resolve(entity).rootExternalId());
        assertEquals(1, codeReader.calls);

        entity.setBasicConfig("""
                {"serverUrl":"ldap://127.0.0.1:389","baseDn":"dc=example,dc=com",
                 "rootDeptId":"root-b"}
                """);

        assertEquals("root-b", resolver.resolve(entity).rootExternalId());
    }

    @Test
    void shouldRejectUnknownPersistedProvider() {
        IdentitySourceEntity entity = entity("unknown-1", "{}");
        codeReader.codes.put("unknown-1", "unknown");

        assertThrows(IdentitySourceProviderNotSupportedException.class,
                () -> resolver.resolve(entity));
    }

    @Test
    void shouldRejectMissingPersistedProvider() {
        IdentitySourceEntity entity = entity("missing-1", "{}");

        assertThrows(IdentitySourceProviderNotSupportedException.class,
                () -> resolver.resolveProviderCode(entity));
    }

    private IdentitySourceEntity entity(String id, String basicConfig) {
        IdentitySourceEntity entity = new IdentitySourceEntity();
        entity.setId(id);
        entity.setBasicConfig(basicConfig);
        return entity;
    }

    // Reader returning provider codes registered by the tests
    private static final class FakeProviderCodeReader implements IdentitySourceProviderCodeReader {

        private final Map<String, String> codes = new HashMap<>();
        private int calls;

        @Override
        public Map<String, String> findAllProviderCodes() {
            calls++;
            return codes;
        }
    }
}