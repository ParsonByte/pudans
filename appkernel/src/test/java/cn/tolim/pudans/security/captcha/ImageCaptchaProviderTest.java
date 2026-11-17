package cn.tolim.pudans.security.captcha;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ImageCaptchaProviderTest {

    @Mock
    CaptchaChallengeStore challengeStore;

    @Mock
    CaptchaRenderer renderer;

    private ImageCaptchaProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ImageCaptchaProvider(challengeStore, renderer);
    }

    @Test
    void challengeStoresTheAnswerAndReturnsTheImage() {
        when(renderer.render(any(ParameterSetting.Captcha.class)))
                .thenReturn(new CaptchaRenderer.RenderedCaptcha("ABC12", "data:image/png;base64,AAAA"));
        when(challengeStore.save(anyString(), eq("ABC12"), any(Duration.class))).thenReturn(Mono.empty());

        StepVerifier.create(provider.challenge(captcha()))
                .assertNext(challenge -> {
                    assertThat(challenge.challengeId()).isNotBlank();
                    assertThat(challenge.image()).startsWith("data:image/png;base64,");
                    assertThat(challenge.expiresInSeconds()).isEqualTo(120);
                })
                .verifyComplete();

        verify(challengeStore).save(anyString(), eq("ABC12"), any(Duration.class));
    }

    @Test
    void verifyDeletesTheChallengeOnSuccess() {
        when(challengeStore.get("challenge-1")).thenReturn(Mono.just("ABC12"));
        when(challengeStore.delete("challenge-1")).thenReturn(Mono.just(true));

        StepVerifier.create(provider.verify("challenge-1", "abc12"))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void verifyRejectsAWrongAnswer() {
        when(challengeStore.get("challenge-1")).thenReturn(Mono.just("ABC12"));
        when(challengeStore.delete("challenge-1")).thenReturn(Mono.just(false));

        StepVerifier.create(provider.verify("challenge-1", "wrong"))
                .expectNext(false)
                .verifyComplete();

        verify(challengeStore).delete("challenge-1");
    }

    @Test
    void verifyRejectsMissingInputs() {
        StepVerifier.create(provider.verify(null, "ABC12")).expectNext(false).verifyComplete();
        StepVerifier.create(provider.verify("challenge-1", "")).expectNext(false).verifyComplete();
    }

    private static ParameterSetting.Captcha captcha() {
        var captcha = new ParameterSetting.Captcha();
        captcha.setCaptchaProvider("image");
        captcha.setCaptchaMode("TEXT");
        captcha.setCaptchaLength(5);
        return captcha;
    }
}
