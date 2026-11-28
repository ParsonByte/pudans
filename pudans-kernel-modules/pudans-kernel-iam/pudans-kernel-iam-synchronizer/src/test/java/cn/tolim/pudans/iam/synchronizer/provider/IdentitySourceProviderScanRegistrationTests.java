package cn.tolim.pudans.iam.synchronizer.provider;

import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderAutoConfiguration;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderRegistry;
import cn.tolim.pudans.iam.identitysource.ldap.LdapIdentitySourceAutoConfiguration;
import cn.tolim.pudans.iam.identitysource.scim.ScimIdentitySourceAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;


class IdentitySourceProviderScanRegistrationTests {

    // Mirrors the IAM application, which component scans the shared package and also loads
    // Spring Boot auto-configurations from the provider modules
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ScannedConfiguration.class)
            .withConfiguration(AutoConfigurations.of(
                    IdentitySourceProviderAutoConfiguration.class,
                    LdapIdentitySourceAutoConfiguration.class,
                    ScimIdentitySourceAutoConfiguration.class,
                    LegacyIdentitySourceProviderAutoConfiguration.class));

    @Test
    void shouldRegisterProvidersWhenPackageIsScannedAndAutoConfigured() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(IdentitySourceProviderRegistry.class);
            IdentitySourceProviderRegistry registry =
                    context.getBean(IdentitySourceProviderRegistry.class);
            assertThat(registry.getProviders())
                    .extracting(definition -> definition.getCode())
                    .contains("ldap", "scim", "dingtalk", "feishu");
        });
    }

    @Configuration
    @ComponentScan(basePackages = "cn.tolim.pudans.iam.identitysource")
    static class ScannedConfiguration {
    }
}