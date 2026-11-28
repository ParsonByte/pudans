package cn.tolim.pudans.iam.synchronizer.provider;

import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderAutoConfiguration;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;


class LegacyIdentitySourceProviderAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentitySourceProviderAutoConfiguration.class,
                    LegacyIdentitySourceProviderAutoConfiguration.class));

    @Test
    void shouldRegisterLegacyProviderBeans() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(DingTalkIdentitySourceProvider.class);
            assertThat(context).hasSingleBean(FeiShuIdentitySourceProvider.class);
            IdentitySourceProviderRegistry registry =
                    context.getBean(IdentitySourceProviderRegistry.class);
            assertThat(registry.supports("dingtalk")).isTrue();
            assertThat(registry.supports("feishu")).isTrue();
        });
    }
}