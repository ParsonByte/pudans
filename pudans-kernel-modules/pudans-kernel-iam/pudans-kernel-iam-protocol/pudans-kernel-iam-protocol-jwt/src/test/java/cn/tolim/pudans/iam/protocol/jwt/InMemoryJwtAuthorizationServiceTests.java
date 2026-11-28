package cn.tolim.pudans.iam.protocol.jwt;

import cn.tolim.pudans.iam.appkernel.core.ApplicationServiceLoader;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


class InMemoryJwtAuthorizationServiceTests {

    private final InMemoryJwtAuthorizationService service = new InMemoryJwtAuthorizationService(
            mock(ApplicationServiceLoader.class));

    private static JwtAuthentication authorization(String id, String token, Instant expiresAt) {
        return JwtAuthentication.builder()
                .id(id)
                .token(token)
                .issuer("https://iam.pudans.test")
                .subject("user-1")
                .audience("app-1")
                .clientId("client-1")
                .principal(mock(Authentication.class))
                .issuedAt(expiresAt.minusSeconds(60))
                .expiresAt(expiresAt)
                .build();
    }

    @Test
    void savesFindsByIdAndTokenAndRemoves() {
        JwtAuthentication authorization = authorization("id-1", "token-1",
                Instant.now().plusSeconds(60));

        service.save(authorization);

        assertThat(service.findById("id-1")).isSameAs(authorization);
        assertThat(service.findByToken("token-1")).isSameAs(authorization);

        service.remove(authorization);

        assertThat(service.findById("id-1")).isNull();
        assertThat(service.findByToken("token-1")).isNull();
    }

    @Test
    void replacesTokenIndexWhenAuthorizationIsSavedAgain() {
        JwtAuthentication first = authorization("id-1", "token-1",
                Instant.now().plusSeconds(60));
        JwtAuthentication second = authorization("id-1", "token-2",
                Instant.now().plusSeconds(60));

        service.save(first);
        service.save(second);

        assertThat(service.findById("id-1")).isSameAs(second);
        assertThat(service.findByToken("token-1")).isNull();
        assertThat(service.findByToken("token-2")).isSameAs(second);
    }

    @Test
    void returnsNullForExpiredAuthorizationFoundById() {
        JwtAuthentication authorization = authorization("id-expired", "token-expired",
                Instant.now().minusSeconds(1));
        service.save(authorization);

        assertThat(service.findById("id-expired")).isNull();
        assertThat(service.findByToken("token-expired")).isNull();
    }

    @Test
    void returnsNullForExpiredAuthorizationFoundByToken() {
        JwtAuthentication authorization = authorization("id-expired", "token-expired",
                Instant.now().minusSeconds(1));
        service.save(authorization);

        assertThat(service.findByToken("token-expired")).isNull();
        assertThat(service.findById("id-expired")).isNull();
    }
}
