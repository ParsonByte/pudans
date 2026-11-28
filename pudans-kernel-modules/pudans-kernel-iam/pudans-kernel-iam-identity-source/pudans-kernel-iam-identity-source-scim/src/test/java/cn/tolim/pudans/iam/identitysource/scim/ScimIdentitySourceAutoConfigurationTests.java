package cn.tolim.pudans.iam.identitysource.scim;

import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderAutoConfiguration;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;


class ScimIdentitySourceAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentitySourceProviderAutoConfiguration.class,
                    ScimIdentitySourceAutoConfiguration.class));

    @Test
    void shouldRegisterScimProviderBean() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(ScimIdentitySourceProvider.class);
            IdentitySourceProviderRegistry registry =
                    context.getBean(IdentitySourceProviderRegistry.class);
            assertThat(registry.supports("scim")).isTrue();
            assertThat(registry.getRequired("scim"))
                    .isInstanceOf(ScimIdentitySourceProvider.class);
        });
    }
}