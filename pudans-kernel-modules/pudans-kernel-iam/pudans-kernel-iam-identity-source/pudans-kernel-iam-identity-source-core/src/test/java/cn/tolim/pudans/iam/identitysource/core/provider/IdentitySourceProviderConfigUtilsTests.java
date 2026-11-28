package cn.tolim.pudans.iam.identitysource.core.provider;

import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import cn.tolim.pudans.iam.identitysource.core.exception.IdentitySourceNotConfiguredException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;


class IdentitySourceProviderConfigUtilsTests {

    private final TestProvider provider = new TestProvider();

    @Test
    void shouldReadConfigurationOfProviderType() {
        IdentitySourceConfig config = IdentitySourceProviderConfigUtils.readConfig(provider,
                """
                        {"endpoint":"https://example.com","rootId":"root-1"}
                        """);

        TestConfig typed = assertInstanceOf(TestConfig.class, config);
        assertEquals("https://example.com", typed.getEndpoint());
        assertEquals("root-1", typed.getRootId());
    }

    @Test
    void shouldRejectBlankConfiguration() {
        assertThrows(IdentitySourceNotConfiguredException.class,
                () -> IdentitySourceProviderConfigUtils.readConfig(provider, " "));
        assertThrows(IdentitySourceNotConfiguredException.class,
                () -> IdentitySourceProviderConfigUtils.readConfig(provider, null));
    }

    @Test
    void shouldRejectInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> IdentitySourceProviderConfigUtils.readConfig(provider, "{invalid"));
    }

    // Configuration used by the read tests
    public static class TestConfig extends IdentitySourceConfig {
        private String endpoint;
        private String rootId;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getRootId() {
            return rootId;
        }

        public void setRootId(String rootId) {
            this.rootId = rootId;
        }
    }

    // Definition exposing the test configuration type
    private static final class TestProvider implements IdentitySourceProviderDefinition {

        @Override
        public String getCode() {
            return "test";
        }

        @Override
        public String getName() {
            return "Test";
        }

        @Override
        public Class<? extends IdentitySourceConfig> getConfigType() {
            return TestConfig.class;
        }

        @Override
        public IdentitySourceConfigValidator<? extends IdentitySourceConfig> getConfigValidator() {
            return null;
        }

        @Override
        public IdentitySourceClient createClient(IdentitySourceConfig config) {
            return null;
        }

        @Override
        public Class<? extends IdentitySource<? extends IdentitySourceConfig>> getIdentitySourceType() {
            return null;
        }

        @Override
        public String getRootExternalId(IdentitySourceConfig config) {
            return ((TestConfig) config).getRootId();
        }

        @Override
        public String getDataOriginType() {
            return "test";
        }

        @Override
        public String getDataOriginName() {
            return "Test";
        }
    }
}