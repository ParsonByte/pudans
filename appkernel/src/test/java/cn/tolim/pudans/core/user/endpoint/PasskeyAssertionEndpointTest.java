package cn.tolim.pudans.core.user.endpoint;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.properties.PasskeyProperties;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import cn.tolim.pudans.infra.webauthn.Passkeys;
import cn.tolim.pudans.security.authentication.ExternalLogins;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
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

    @Test
    void assertionIsForbiddenWhenPasskeyLoginIsDisabled() {
        var session = new ParameterSetting.Session();
        session.setPasskeyLoginEnabled(false);
        when(parameterGroups.getSession()).thenReturn(Mono.just(session));
        var endpoint = new PasskeyAssertionEndpoint(
                passkeyService, parameterGroups, passkeyProperties, externalLogins);

        WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .build()
                .post()
                .uri("/passkeys/assertion/options")
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
