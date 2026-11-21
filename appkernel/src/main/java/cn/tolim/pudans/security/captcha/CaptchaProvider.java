package cn.tolim.pudans.security.captcha;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import reactor.core.publisher.Mono;

public interface CaptchaProvider {

    // 提供方类型标识，供配置选择实现
    String type();

    // 生成挑战并按需保存验证答案
    Mono<Captchas.CaptchaChallenge> challenge(ParameterSetting.Captcha config);

    // 校验并消费一次性验证码答案
    Mono<Boolean> verify(String challengeId, String answer);
}
