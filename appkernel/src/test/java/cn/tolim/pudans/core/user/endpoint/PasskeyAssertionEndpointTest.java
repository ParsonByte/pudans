package cn.tolim.pudans.core.user.endpoint;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.user.UserConnection;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.properties.PasskeyProperties;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import cn.tolim.pudans.infra.webauthn.Passkeys;
import cn.tolim.pudans.security.authentication.ExternalLogins;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PasskeyAssertionEndpointTest {

    @Mock
    Passkeys passkeyService;

    @Mock
    PasskeyProperties passkeyProperties;

    @Mock
    ExternalLogins externalLogins;

    @Mock
    ParameterGroups parameterGroups;

    @Mock
    Accounts accounts;

    @Test
    void assertionIsForbiddenWhenPasskeyLoginIsDisabled() {
        var session = new ParameterSetting.Session();
        session.setPasskeyLoginEnabled(false);
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));
        var endpoint = new PasskeyAssertionEndpoint(
                passkeyService, accounts, parameterGroups, passkeyProperties, externalLogins);

        WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .build()
                .post()
                .uri("/passkeys/assertion/options")
                .exchange()
                .expectStatus()
                .isForbidden();
    }

    @Test
    void assertionOptionsResolveACustomLoginNameToTheResourceName() {
        var session = new ParameterSetting.Session();
        session.setPasskeyLoginEnabled(true);
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));
        when(passkeyProperties.getTimeout()).thenReturn(60000L);
        when(passkeyProperties.getRpId()).thenReturn("localhost");
        when(passkeyService.newChallenge()).thenReturn("challenge");
        when(accounts.findUserByLoginName("alice.login")).thenReturn(Mono.just(user("alice")));
        when(passkeyService.listPasskeys("alice")).thenReturn(Flux.just(passkey()));
        var endpoint = new PasskeyAssertionEndpoint(
                passkeyService, accounts, parameterGroups, passkeyProperties, externalLogins);

        WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .build()
                .post()
                .uri("/passkeys/assertion/options")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "alice.login"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.publicKey.challenge")
                .isEqualTo("challenge")
                .jsonPath("$.publicKey.allowCredentials[0].id")
                .isEqualTo("credential-1");
    }

    private static User user(String name) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(name);
        user.setMetadata(metadata);
        user.setSpec(new User.UserSpec());
        return user;
    }

    private static UserConnection passkey() {
        var connection = new UserConnection();
        var metadata = new Metadata();
        metadata.setName("alice-passkey");
        connection.setMetadata(metadata);
        var spec = new UserConnection.UserConnectionSpec();
        spec.setProviderUserId("credential-1");
        connection.setSpec(spec);
        return connection;
    }
}
