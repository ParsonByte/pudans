package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import reactor.core.publisher.Mono;

public interface CaptchaProvider {

    String type();

    Mono<CaptchaService.CaptchaChallenge> challenge(ParameterSetting.Captcha config);

    Mono<Boolean> verify(String challengeId, String answer);
}
