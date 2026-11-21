package cn.tolim.pudans.security.captcha;

import reactor.core.publisher.Mono;

public interface Captchas {

    // 按当前配置下发新的验证码挑战
    Mono<CaptchaChallenge> newChallenge();

    // 校验一次性验证码答案是否正确
    Mono<Boolean> verify(String challengeId, String answer);

    // 登录是否需要验证码由参数配置决定
    Mono<Boolean> loginEnabled();

    // 找回流程是否需要验证码由配置决定
    Mono<Boolean> recoveryEnabled();

    record CaptchaChallenge(String challengeId, String image, long expiresInSeconds) {}
}
