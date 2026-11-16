package cn.tolim.pudans.security.authentication.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.security.authentication.CryptoService;
import cn.tolim.pudans.core.risk.service.RiskControlService;
import cn.tolim.pudans.security.captcha.CaptchaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.BadCredentialsException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class LoginAuthenticationConverterTest {

    @Mock
    CryptoService cryptoService;

    @Mock
    RiskControlService riskControlService;

    @Mock
    CaptchaService captchaService;

    @Test
    void rejectsLoginWhenCaptchaVerificationFails() {
        when(captchaService.loginEnabled()).thenReturn(Mono.just(true));
        when(captchaService.verify(anyString(), anyString())).thenReturn(Mono.just(false));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=alice&password=secret&captchaChallengeId=c1&captchaAnswer=wrong"));
        var converter = new LoginAuthenticationConverter(cryptoService, riskControlService, captchaService);

        StepVerifier.create(converter.convert(exchange))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BadCredentialsException.class)
                        .hasMessageContaining("Captcha"))
                .verify();
    }

    @Test
    void skipsCaptchaWhenItIsDisabled() {
        when(captchaService.loginEnabled()).thenReturn(Mono.just(false));
        when(cryptoService.decrypt(any())).thenReturn(Mono.just("secret".getBytes()));
        when(riskControlService.evaluate(any(), any())).thenReturn(Mono.empty());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=alice&password=c2VjcmV0"));
        var converter = new LoginAuthenticationConverter(cryptoService, riskControlService, captchaService);

        StepVerifier.create(converter.convert(exchange)).verifyComplete();
    }
}
