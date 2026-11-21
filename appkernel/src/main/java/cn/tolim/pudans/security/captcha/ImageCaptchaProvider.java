package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class ImageCaptchaProvider implements CaptchaProvider {

    public static final String TYPE = "image";

    private static final Duration CHALLENGE_TTL = Duration.ofSeconds(120);

    private final CaptchaChallengeStore challengeStore;

    private final CaptchaRenderer renderer;

    // 图片验证码的配置标识
    @Override
    public String type() {
        return TYPE;
    }

    // 渲染图片并把答案存入一次性挑战存储
    @Override
    public Mono<Captchas.CaptchaChallenge> challenge(ParameterSetting.Captcha config) {
        var rendered = renderer.render(config);
        var challengeId = UUID.randomUUID().toString();
        return challengeStore
                .save(challengeId, rendered.answer(), CHALLENGE_TTL)
                .thenReturn(new Captchas.CaptchaChallenge(
                        challengeId, rendered.imageDataUrl(), CHALLENGE_TTL.toSeconds()));
    }

    // 忽略大小写比对并销毁挑战，防止重放
    @Override
    public Mono<Boolean> verify(String challengeId, String answer) {
        if (challengeId == null
                || challengeId.isBlank()
                || answer == null
                || answer.isBlank()) {
            return Mono.just(false);
        }
        return challengeStore
                .take(challengeId)
                .map(stored -> Objects.equals(
                        stored.toLowerCase(Locale.ROOT), answer.trim().toLowerCase(Locale.ROOT)))
                .defaultIfEmpty(false);
    }
}
