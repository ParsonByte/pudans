package cn.tolim.pudans.iam.protocol.oidc.authorization.client;

import cn.tolim.pudans.iam.communal.entity.app.AppEntity;
import cn.tolim.pudans.iam.communal.entity.app.AppOidcConfigEntity;
import cn.tolim.pudans.iam.communal.repository.app.AppOidcConfigRepository;
import cn.tolim.pudans.iam.communal.repository.app.AppRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;


class OidcConfigRegisteredClientRepositoryTests {

    @Test
    void savePersistsApplicationAndProtocolSettings() {
        AppOidcConfigRepository configRepository = mock(AppOidcConfigRepository.class);
        AppRepository appRepository = mock(AppRepository.class);
        OidcConfigRegisteredClientRepository repository =
                new OidcConfigRegisteredClientRepository(configRepository, appRepository);
        AppEntity app = new AppEntity();
        app.setId("app-1");
        AppOidcConfigEntity config = new AppOidcConfigEntity();
        config.setAppId("app-1");
        when(appRepository.findById("app-1")).thenReturn(Optional.of(app));
        when(configRepository.findByAppId("app-1")).thenReturn(Optional.of(config));
        RegisteredClient registeredClient = RegisteredClient.withId("app-1")
                .clientId("client-1")
                .clientSecret("client-secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback")
                .postLogoutRedirectUri("https://client.example/logout")
                .scope(OidcScopes.OPENID)
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(true)
                        .requireProofKey(true)
                        .tokenEndpointAuthenticationSigningAlgorithm(SignatureAlgorithm.RS256)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .authorizationCodeTimeToLive(Duration.ofMinutes(5))
                        .deviceCodeTimeToLive(Duration.ofMinutes(6))
                        .refreshTokenTimeToLive(Duration.ofDays(30))
                        .accessTokenTimeToLive(Duration.ofMinutes(20))
                        .idTokenSignatureAlgorithm(SignatureAlgorithm.RS256)
                        .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)
                        .reuseRefreshTokens(true)
                        .build())
                .build();
        repository.save(registeredClient);
        assertThat(app.getClientId()).isEqualTo("client-1");
        assertThat(app.getClientSecret()).isEqualTo("client-secret");
        assertThat(app.getConfigured()).isTrue();
        assertThat(config.getClientAuthMethods())
                .containsExactly(ClientAuthenticationMethod.CLIENT_SECRET_BASIC.getValue());
        assertThat(config.getAuthGrantTypes())
                .containsExactly(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());
        assertThat(config.getRedirectUris())
                .containsExactly("https://client.example/callback");
        assertThat(config.getPostLogoutRedirectUris())
                .containsExactly("https://client.example/logout");
        assertThat(config.getGrantScopes()).containsExactly(OidcScopes.OPENID);
        assertThat(config.getRequireAuthConsent()).isTrue();
        assertThat(config.getRequireProofKey()).isTrue();
        assertThat(config.getTokenEndpointAuthSigningAlgorithm())
                .isEqualTo(SignatureAlgorithm.RS256.getName());
        assertThat(config.getAuthorizationCodeTimeToLive()).isEqualTo(Duration.ofMinutes(5));
        assertThat(config.getDeviceCodeTimeToLive()).isEqualTo(Duration.ofMinutes(6));
        assertThat(config.getRefreshTokenTimeToLive()).isEqualTo(Duration.ofDays(30));
        assertThat(config.getAccessTokenTimeToLive()).isEqualTo(Duration.ofMinutes(20));
        assertThat(config.getIdTokenSignatureAlgorithm())
                .isEqualTo(SignatureAlgorithm.RS256.getName());
        assertThat(config.getAccessTokenFormat())
                .isEqualTo(OAuth2TokenFormat.SELF_CONTAINED.getValue());
        assertThat(config.getReuseRefreshToken()).isTrue();
        verify(configRepository).save(config);
        verify(appRepository).save(app);
    }

    @Test
    void saveRejectsUnknownApplication() {
        AppOidcConfigRepository configRepository = mock(AppOidcConfigRepository.class);
        AppRepository appRepository = mock(AppRepository.class);
        OidcConfigRegisteredClientRepository repository =
                new OidcConfigRegisteredClientRepository(configRepository, appRepository);
        RegisteredClient registeredClient = RegisteredClient.withId("missing-app")
                .clientId("client-1")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback")
                .build();
        when(appRepository.findById("missing-app")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> repository.save(registeredClient))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IAM 应用不存在");
    }
}
