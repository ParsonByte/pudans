package cn.tolim.pudans.security.authentication.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.setting.MasterKeys;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MasterKeyRotationScopeTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ReactiveJwtDecoder jwtDecoder;

    @Mock
    Roles roleService;

    @Mock
    cn.tolim.pudans.core.rbac.group.Groups groups;

    @Mock
    MasterKeys masterKeys;

    @Test
    void rsaJwtsRemainValidAcrossMasterKeyRotation() {
        var jwt = Jwt.withTokenValue("rsa-jwt")
                .header("alg", "RS256")
                .claim("sub", "alice")
                .claim("roles", List.of("normal-role"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(jwtDecoder.decode("rsa-jwt")).thenReturn(Mono.just(jwt));
        var manager = new TokenAuthenticationManager(client, jwtDecoder, roleService, groups, masterKeys);

        StepVerifier.create(manager.authenticate(new BearerTokenAuthenticationToken("rsa-jwt")))
                .assertNext(authentication -> assertThat(authentication.getName()).isEqualTo("alice"))
                .verifyComplete();
    }

    @Test
    void legacyPatJwtsAreRejectedAfterMasterKeyRotation() {
        when(masterKeys.currentVersion()).thenReturn(1);
        var manager = new TokenAuthenticationManager(client, jwtDecoder, roleService, groups, masterKeys);

        StepVerifier.create(manager.authenticate(new BearerTokenAuthenticationToken("pat_legacy.jwt")))
                .expectError(InvalidBearerTokenException.class)
                .verify();
    }
}
