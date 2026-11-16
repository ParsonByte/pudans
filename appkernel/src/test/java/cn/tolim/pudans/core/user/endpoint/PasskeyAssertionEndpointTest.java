package cn.tolim.pudans.core.user.endpoint;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.infra.properties.PasskeyProperties;
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import cn.tolim.pudans.infra.webauthn.PasskeyService;
import cn.tolim.pudans.security.authentication.ExternalLoginCompletionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class PasskeyAssertionEndpointTest {

    @Mock
    PasskeyService passkeyService;

    @Mock
    PasskeyProperties passkeyProperties;

    @Mock
    ExternalLoginCompletionService externalLoginCompletionService;

    @Mock
    ParameterSettingGroupService parameterSettingGroupService;

    @Test
    void assertionIsForbiddenWhenPasskeyLoginIsDisabled() {
        var session = new ParameterSetting.Session();
        session.setPasskeyLoginEnabled(false);
        when(parameterSettingGroupService.getSession()).thenReturn(Mono.just(session));
        var endpoint = new PasskeyAssertionEndpoint(
                passkeyService, parameterSettingGroupService, passkeyProperties, externalLoginCompletionService);

        WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .build()
                .post()
                .uri("/passkeys/assertion/options")
                .exchange()
                .expectStatus()
                .isForbidden();
    }
}
