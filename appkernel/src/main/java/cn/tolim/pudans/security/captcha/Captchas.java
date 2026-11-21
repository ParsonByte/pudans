package cn.tolim.pudans.security.captcha;

import reactor.core.publisher.Mono;

public interface Captchas {

    Mono<CaptchaChallenge> newChallenge();

    Mono<Boolean> verify(String challengeId, String answer);

    Mono<Boolean> loginEnabled();

    Mono<Boolean> recoveryEnabled();

    record CaptchaChallenge(String challengeId, String image, long expiresInSeconds) {}
}
