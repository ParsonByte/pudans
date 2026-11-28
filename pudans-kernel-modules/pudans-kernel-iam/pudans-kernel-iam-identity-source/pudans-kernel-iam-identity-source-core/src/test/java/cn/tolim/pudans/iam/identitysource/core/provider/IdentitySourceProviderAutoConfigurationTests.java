package cn.tolim.pudans.iam.identitysource.core.provider;

import cn.tolim.pudans.iam.identitysource.core.IdentitySource;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfig;
import cn.tolim.pudans.iam.identitysource.core.IdentitySourceConfigValidator;
import cn.tolim.pudans.iam.identitysource.core.client.IdentitySourceClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;


class IdentitySourceProviderAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentitySourceProviderAutoConfiguration.class));

    @Test
    void shouldRegisterProviderRegistry() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(IdentitySourceProviderRegistry.class);
            IdentitySourceProviderRegistry registry =
                    context.getBean(IdentitySourceProviderRegistry.class);
            assertThat(registry.getProviders()).isEmpty();
        });
    }

    @Test
    void shouldCollectProviderDefinitions() {
        runner.withBean(TestProvider.class, TestProvider::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(IdentitySourceProviderRegistry.class);
                    IdentitySourceProviderRegistry registry =
                            context.getBean(IdentitySourceProviderRegistry.class);
                    assertThat(registry.supports("test")).isTrue();
                    assertThat(registry.getRequired("test")).isInstanceOf(TestProvider.class);
                });
    }

    // Provider definition contributed as a test bean
    static class TestProvider implements IdentitySourceProviderDefinition {

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
            return "test";
        }

        @Override
        public String getDataOriginName() {
            return "Test";
        }
    }
}