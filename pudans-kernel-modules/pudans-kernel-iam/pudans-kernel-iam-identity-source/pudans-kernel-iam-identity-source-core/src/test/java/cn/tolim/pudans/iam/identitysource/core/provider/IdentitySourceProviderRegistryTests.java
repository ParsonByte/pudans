package cn.tolim.pudans.iam.identitysource.core.provider;

import cn.tolim.pudans.iam.communal.exception.IdentitySourceProviderNotSupportedException;
import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;


class IdentitySourceProviderRegistryTests {

    @Test
    void shouldFindRegisteredProviderByCode() {
        TestProvider provider = new TestProvider("custom");
        IdentitySourceProviderRegistry registry = new IdentitySourceProviderRegistry(
                List.of(provider));

        assertTrue(registry.supports("custom"));
        assertSame(provider, registry.getRequired("custom"));
        assertTrue(registry.find("custom").isPresent());
        assertEquals(1, registry.getProviders().size());
    }

    @Test
    void shouldRejectUnknownProviderCode() {
        IdentitySourceProviderRegistry registry = new IdentitySourceProviderRegistry(
                List.of(new TestProvider("custom")));

        assertFalse(registry.supports("missing"));
        assertTrue(registry.find("missing").isEmpty());
        assertThrows(IdentitySourceProviderNotSupportedException.class,
                () -> registry.getRequired("missing"));
        assertThrows(IdentitySourceProviderNotSupportedException.class,
                () -> registry.getRequired(null));
    }

    @Test
    void shouldRejectDuplicateProviderCodes() {
        assertThrows(IllegalStateException.class,
                () -> new IdentitySourceProviderRegistry(
                        List.of(new TestProvider("custom"), new TestProvider("custom"))));
    }

    @Test
    void shouldRejectBlankProviderCode() {
        assertThrows(IllegalStateException.class,
                () -> new IdentitySourceProviderRegistry(List.of(new TestProvider(" "))));
    }

    @Test
    void shouldAcceptEmptyProviderList() {
        IdentitySourceProviderRegistry registry = new IdentitySourceProviderRegistry(null);

        assertTrue(registry.getProviders().isEmpty());
    }

    // Provider definition used only by the registry tests
    private static final class TestProvider implements IdentitySourceProviderDefinition {

        private final String code;

        private TestProvider(String code) {
            this.code = code;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getName() {
            return code;
        }

        @Override
        public Class<? extends IdentitySourceConfig> getConfigType() {
            return IdentitySourceConfig.class;
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
            return null;
        }

        @Override
        public String getDataOriginType() {
            return code;
        }

        @Override
        public String getDataOriginName() {
            return code;
        }
    }
}