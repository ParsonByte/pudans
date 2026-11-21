package cn.tolim.pudans.security.authentication.rememberme;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface CookieSignatureKeyResolver {
    Mono<String> resolveSigningKey();
}
