package cn.tolim.pudans.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.infra.properties.OidcProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class OidcClientRegistrationConfigurationTest {

    private final OidcClientRegistrationConfiguration configuration = new OidcClientRegistrationConfiguration();

    @Test
    void mapsConfiguredProvidersToClientRegistrations() {
        var provider = new OidcProperties.Provider();
        provider.setRegistrationId("example");
        provider.setDisplayName("Example");
        provider.setAuthorizationUri("https://idp.example.com/authorize");
        provider.setTokenUri("https://idp.example.com/token");
        provider.setUserInfoUri("https://idp.example.com/userinfo");
        provider.setClientId("client");
        provider.setClientSecret("secret");
        provider.setScopes(List.of("openid", "email"));
        var properties = new OidcProperties();
        properties.setProviders(List.of(provider));

        var repository = configuration.clientRegistrationRepository(properties);

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
    void returnsAnEmptyRepositoryWhenNoProvidersAreConfigured() {
        var properties = new OidcProperties();
        properties.setProviders(List.of());

        var repository = configuration.clientRegistrationRepository(properties);

        StepVerifier.create(repository.findByRegistrationId("missing")).verifyComplete();
    }
}
