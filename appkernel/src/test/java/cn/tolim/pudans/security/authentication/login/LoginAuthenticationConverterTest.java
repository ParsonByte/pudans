package cn.tolim.pudans.security.authentication.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.security.authentication.LoginCipher;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.core.risk.RiskDecision;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.captcha.Captchas;
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
    LoginCipher loginCipher;

    @Mock
    RiskGuard riskGuard;

    @Mock
    Captchas captchas;

    @Mock
    SecurityParameters securitySettings;

    @Test
    void rejectsLoginWhenCaptchaVerificationFails() {
        when(captchas.loginEnabled()).thenReturn(Mono.just(true));
        when(captchas.verify(anyString(), anyString())).thenReturn(Mono.just(false));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=alice&password=secret&captchaChallengeId=c1&captchaAnswer=wrong"));
        var converter = new LoginAuthenticationConverter(
                loginCipher, riskGuard, captchas, securitySettings);

        StepVerifier.create(converter.convert(exchange))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BadCredentialsException.class)
                        .hasMessageContaining("验证码"))
                .verify();
    }

    @Test
    void skipsCaptchaWhenItIsDisabled() {
        when(captchas.loginEnabled()).thenReturn(Mono.just(false));
        when(securitySettings.isRsaLoginEncryptionEnabled()).thenReturn(true);
        when(loginCipher.decrypt(any())).thenReturn(Mono.just("secret".getBytes()));
        when(riskGuard.evaluate(any(), any())).thenReturn(Mono.empty());
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=alice&password=c2VjcmV0"));
        var converter = new LoginAuthenticationConverter(
                loginCipher, riskGuard, captchas, securitySettings);

        StepVerifier.create(converter.convert(exchange)).verifyComplete();
    }

    @Test
    void acceptsPlainPasswordWhenRsaEncryptionIsDisabled() {
        when(captchas.loginEnabled()).thenReturn(Mono.just(false));
        when(securitySettings.isRsaLoginEncryptionEnabled()).thenReturn(false);
        when(riskGuard.evaluate(any(), any())).thenReturn(Mono.just(RiskDecision.permit()));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("username=alice&password=plain-secret"));
        var converter = new LoginAuthenticationConverter(
                loginCipher, riskGuard, captchas, securitySettings);

        StepVerifier.create(converter.convert(exchange))
                .assertNext(authentication -> {
                    assertThat(authentication.getPrincipal()).isEqualTo("alice");
                    assertThat(authentication.getCredentials()).isEqualTo("plain-secret");
                })
                .verifyComplete();
    }

    @Test
    void missingUsernameIsRejectedWithoutANullPointer() {
        when(captchas.loginEnabled()).thenReturn(Mono.just(false));
        when(securitySettings.isRsaLoginEncryptionEnabled()).thenReturn(true);
        when(loginCipher.decrypt(any())).thenReturn(Mono.just("secret".getBytes()));
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/login")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body("password=c2VjcmV0"));
        var converter = new LoginAuthenticationConverter(
                loginCipher, riskGuard, captchas, securitySettings);

        StepVerifier.create(converter.convert(exchange))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(BadCredentialsException.class)
                        .hasMessageContaining("用户名或密码错误"))
                .verify();
    }
}
