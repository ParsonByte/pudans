package cn.tolim.pudans.core.security.authentication.rememberme;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface CookieSignatureKeyResolver {
    Mono<String> resolveSigningKey();
}
