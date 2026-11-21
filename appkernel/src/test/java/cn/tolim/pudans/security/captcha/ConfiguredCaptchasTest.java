package cn.tolim.pudans.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.api.ParameterSetting;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ConfiguredCaptchasTest {

    @Mock
    ParameterGroups parameterGroups;

    private CaptchaProvider imageProvider;

    private CaptchaProvider customProvider;

    private ConfiguredCaptchas service;

    @BeforeEach
    void setUp() {
        imageProvider = mock(CaptchaProvider.class);
        when(imageProvider.type()).thenReturn("image");
        customProvider = mock(CaptchaProvider.class);
        when(customProvider.type()).thenReturn("custom");
        service = new ConfiguredCaptchas(
                parameterGroups, List.of(imageProvider, customProvider, new NoneCaptchaProvider()));
    }

    @Test
    void dispatchesToTheConfiguredProvider() {
        when(parameterGroups.getCaptcha()).thenReturn(Mono.just(captcha("custom")));
        when(customProvider.challenge(any(ParameterSetting.Captcha.class)))
                .thenReturn(Mono.just(new Captchas.CaptchaChallenge("custom-1", "image", 60)));

        StepVerifier.create(service.newChallenge())
                .assertNext(challenge -> assertThat(challenge.challengeId()).isEqualTo("custom-1"))
                .verifyComplete();
    }

    @Test
    void unknownProvidersFallBackToImage() {
        when(parameterGroups.getCaptcha()).thenReturn(Mono.just(captcha("missing")));
        when(imageProvider.challenge(any(ParameterSetting.Captcha.class)))
                .thenReturn(Mono.just(new Captchas.CaptchaChallenge("image-1", "image", 60)));

        StepVerifier.create(service.newChallenge())
                .assertNext(challenge -> assertThat(challenge.challengeId()).isEqualTo("image-1"))
                .verifyComplete();
        verify(imageProvider).challenge(any(ParameterSetting.Captcha.class));
    }

    @Test
    void noneProviderShortCircuitsVerification() {
        when(parameterGroups.getCaptcha()).thenReturn(Mono.just(captcha("none")));

        StepVerifier.create(service.verify(null, null)).expectNext(true).verifyComplete();
    }

    @Test
    void enabledFlagsComeFromTheCaptchaGroup() {
        var captcha = captcha("image");
        captcha.setLoginCaptchaEnabled(true);
        captcha.setRecoveryCaptchaEnabled(false);
        when(parameterGroups.getCaptcha()).thenReturn(Mono.just(captcha));

        StepVerifier.create(service.loginEnabled()).expectNext(true).verifyComplete();
        StepVerifier.create(service.recoveryEnabled()).expectNext(false).verifyComplete();
    }

    private static ParameterSetting.Captcha captcha(String provider) {
        var captcha = new ParameterSetting.Captcha();
        captcha.setCaptchaProvider(provider);
        captcha.setCaptchaMode("TEXT");
        captcha.setCaptchaLength(5);
        return captcha;
    }
}
