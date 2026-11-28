package cn.tolim.pudans.iam.synchronizer.provider;

import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderAutoConfiguration;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderDefinition;
import cn.tolim.pudans.iam.identitysource.core.provider.IdentitySourceProviderRegistry;
import cn.tolim.pudans.iam.identitysource.ldap.LdapIdentitySourceAutoConfiguration;
import cn.tolim.pudans.iam.identitysource.ldap.LdapIdentitySourceProvider;
import cn.tolim.pudans.iam.identitysource.scim.ScimIdentitySourceAutoConfiguration;
import cn.tolim.pudans.iam.identitysource.scim.ScimIdentitySourceProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;


class IdentitySourceProviderRegistrationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    IdentitySourceProviderAutoConfiguration.class,
                    LdapIdentitySourceAutoConfiguration.class,
                    ScimIdentitySourceAutoConfiguration.class,
                    LegacyIdentitySourceProviderAutoConfiguration.class));

    @Test
    void shouldRegisterEveryIdentitySourceProvider() {
        runner.run(context -> {
            IdentitySourceProviderRegistry registry =
                    context.getBean(IdentitySourceProviderRegistry.class);

            assertThat(registry.getProviders())
                    .extracting(IdentitySourceProviderDefinition::getCode)
                    .containsExactlyInAnyOrder("ldap", "scim", "dingtalk", "feishu");
            assertThat(registry.getRequired("ldap"))
                    .isInstanceOf(LdapIdentitySourceProvider.class);
            assertThat(registry.getRequired("scim"))
                    .isInstanceOf(ScimIdentitySourceProvider.class);
            assertThat(registry.getRequired("dingtalk"))
                    .isInstanceOf(DingTalkIdentitySourceProvider.class);
            assertThat(registry.getRequired("feishu"))
                    .isInstanceOf(FeiShuIdentitySourceProvider.class);
        });
    }
}