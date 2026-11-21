package cn.tolim.pudans.infra.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.infra.properties.OidcProperties;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class OidcClientRegistrationConfigurationTest {

    private final SecurityParameters securitySettings =
            mock(SecurityParameters.class);

    @Test
    void mapsConfiguredOidcProvidersToClientRegistrations() {
        var provider = new OidcProperties.Provider();
        provider.setRegistrationId("example");
        provider.setDisplayName("Example");
        provider.setAuthorizationUri("https://idp.example.com/authorize");
        provider.setTokenUri("https://idp.example.com/token");
        provider.setUserInfoUri("https://idp.example.com/userinfo");
        provider.setClientId("client");
        provider.setClientSecret("secret");
        provider.setScopes(List.of("openid", "email"));
        when(securitySettings.oidcProviders()).thenReturn(List.of(provider));
        when(securitySettings.oauth2Providers()).thenReturn(List.of());

        var repository = new SecurityClientRegistrationRepository(securitySettings);

        StepVerifier.create(repository.findByRegistrationId("example"))
                .assertNext(registration -> {
                    assertThat(registration.getClientId()).isEqualTo("client");
                    assertThat(registration.getProviderDetails().getAuthorizationUri())
                            .isEqualTo("https://idp.example.com/authorize");
                    assertThat(registration.getRedirectUri())
                            .isEqualTo("{baseUrl}/login/oauth2/code/{registrationId}");
                })
                .verifyComplete();
    }

    @Test
    void mapsConfiguredOAuth2ProvidersToClientRegistrations() {
        var provider = new ParameterSetting.OAuth2Provider();
        provider.setRegistrationId("github");
        provider.setDisplayName("GitHub");
        provider.setClientId("client");
        provider.setClientSecret("secret");
        provider.setAuthorizationUri("https://github.com/login/oauth/authorize");
        provider.setTokenUri("https://github.com/login/oauth/access_token");
        provider.setUserInfoUri("https://api.github.com/user");
        provider.setUserNameAttribute("login");
        provider.setScopes("read:user user:email");
        when(securitySettings.oauth2Providers()).thenReturn(List.of(provider));
        when(securitySettings.oidcProviders()).thenReturn(List.of());

        var repository = new SecurityClientRegistrationRepository(securitySettings);

        StepVerifier.create(repository.findByRegistrationId("github"))
                .assertNext(registration -> {
                    assertThat(registration.getClientName()).isEqualTo("GitHub");
                    assertThat(registration.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName())
                            .isEqualTo("login");
                    assertThat(registration.getScopes()).containsExactly("read:user", "user:email");
                })
                .verifyComplete();
    }

    @Test
    void returnsAnEmptyRepositoryWhenNoProvidersAreConfigured() {
        when(securitySettings.oauth2Providers()).thenReturn(List.of());
        when(securitySettings.oidcProviders()).thenReturn(List.of());

        var repository = new SecurityClientRegistrationRepository(securitySettings);

        StepVerifier.create(repository.findByRegistrationId("missing")).verifyComplete();
    }
}
