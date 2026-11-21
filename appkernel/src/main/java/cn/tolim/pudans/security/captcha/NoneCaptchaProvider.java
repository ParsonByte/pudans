package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import java.util.UUID;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class NoneCaptchaProvider implements CaptchaProvider {

    public static final String TYPE = "none";

    // 关闭验证码时的配置标识
    @Override
    public String type() {
        return TYPE;
    }

    // 关闭验证码时返回空挑战占位
    @Override
    public Mono<Captchas.CaptchaChallenge> challenge(ParameterSetting.Captcha config) {
        return Mono.just(new Captchas.CaptchaChallenge(UUID.randomUUID().toString(), "", 0));
    }

    // 关闭验证码时所有校验直接通过
    @Override
    public Mono<Boolean> verify(String challengeId, String answer) {
        return Mono.just(true);
    }
}
