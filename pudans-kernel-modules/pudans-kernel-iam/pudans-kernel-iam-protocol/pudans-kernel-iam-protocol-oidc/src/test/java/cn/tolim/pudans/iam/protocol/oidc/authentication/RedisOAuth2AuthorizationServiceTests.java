package cn.tolim.pudans.iam.protocol.oidc.authentication;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RedisOAuth2AuthorizationServiceTests {

    @Test
    void resolvesIdTokenTtlFromTokenExpiry() {
        Instant issuedAt = Instant.now();
        OidcIdToken idToken = new OidcIdToken("id-token", issuedAt,
                issuedAt.plus(Duration.ofMinutes(45)), Map.of("sub", "user"));
        OAuth2Authorization authorization = OAuth2Authorization
                .withRegisteredClient(registeredClient()).id("authorization-id").principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .token(idToken).build();

        Duration ttl = RedisOAuth2AuthorizationService.resolveIdTokenTtl(authorization);

        assertThat(ttl).isNotNull().isBetween(Duration.ofMinutes(44), Duration.ofMinutes(45));
    }

    @Test
    void returnsNullWhenAuthorizationHasNoIdToken() {
        OAuth2Authorization authorization = OAuth2Authorization
                .withRegisteredClient(registeredClient()).id("authorization-id").principalName("user")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .build();

        assertThat(RedisOAuth2AuthorizationService.resolveIdTokenTtl(authorization)).isNull();
    }

    private static RegisteredClient registeredClient() {
        return RegisteredClient.withId("client-id").clientId("client")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("https://client.example/callback").build();
    }
}