package cn.tolim.pudans.security.authentication.recovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.security.captcha.CaptchaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PasswordRecoveryEndpointTest {

    @Mock
    PasswordRecoveryService passwordRecoveryService;

    @Mock
    CaptchaService captchaService;

    @Test
    void rejectsSendCodeWhenRecoveryCaptchaFails() {
        when(captchaService.recoveryEnabled()).thenReturn(Mono.just(true));
        when(captchaService.verify("c1", "wrong")).thenReturn(Mono.just(false));
        var endpoint = new PasswordRecoveryEndpoint(passwordRecoveryService, captchaService);

        StepVerifier.create(endpoint.sendCode(
                        new PasswordRecoveryEndpoint.SendCodeRequest("alice@example.com", "c1", "wrong")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("recovery-captcha-invalid");
                })
                .verify();
    }

    @Test
    void skipsCaptchaWhenRecoveryCaptchaIsDisabled() {
        when(captchaService.recoveryEnabled()).thenReturn(Mono.just(false));
        when(passwordRecoveryService.sendCode("alice@example.com")).thenReturn(Mono.empty());
        var endpoint = new PasswordRecoveryEndpoint(passwordRecoveryService, captchaService);

        StepVerifier.create(endpoint.sendCode(
                        new PasswordRecoveryEndpoint.SendCodeRequest("alice@example.com", null, null)))
                .assertNext(response -> assertThat(response.getStatusCode().value()).isEqualTo(204))
                .verifyComplete();
    }
}
