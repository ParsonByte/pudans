package cn.tolim.pudans.gateway.security;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface GlobalSessionValidator {

    Mono<SessionValidationResult> validate(String sid, String appCode);

    static GlobalSessionValidator disabled() {
        return (sid, appCode) -> Mono.just(SessionValidationResult.inactive("session-validation-disabled"));
    }
}