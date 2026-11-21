package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import reactor.core.publisher.Mono;

public interface CaptchaProvider {

    String type();

    Mono<Captchas.CaptchaChallenge> challenge(ParameterSetting.Captcha config);

    Mono<Boolean> verify(String challengeId, String answer);
}
