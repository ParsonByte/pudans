package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.infra.api.ParameterSetting;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class NoneCaptchaProvider implements CaptchaProvider {

    public static final String TYPE = "none";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public Mono<Captchas.CaptchaChallenge> challenge(ParameterSetting.Captcha config) {
        return Mono.just(new Captchas.CaptchaChallenge(UUID.randomUUID().toString(), "", 0));
    }

    @Override
    public Mono<Boolean> verify(String challengeId, String answer) {
        return Mono.just(true);
    }
}
