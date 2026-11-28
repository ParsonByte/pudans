package cn.tolim.pudans.iam.identitysource.ldap;

import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderAutoConfiguration;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;


class LdapIdentitySourceAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentitySourceProviderAutoConfiguration.class,
                    LdapIdentitySourceAutoConfiguration.class));

    @Test
    void shouldRegisterLdapProviderBean() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(LdapIdentitySourceProvider.class);
            IdentitySourceProviderRegistry registry =
                    context.getBean(IdentitySourceProviderRegistry.class);
            assertThat(registry.supports("ldap")).isTrue();
            assertThat(registry.getRequired("ldap"))
                    .isInstanceOf(LdapIdentitySourceProvider.class);
        });
    }
}