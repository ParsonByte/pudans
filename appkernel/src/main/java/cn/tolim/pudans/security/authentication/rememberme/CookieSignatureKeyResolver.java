package cn.tolim.pudans.security.authentication.rememberme;

import reactor.core.publisher.Mono;

@FunctionalInterface
public interface CookieSignatureKeyResolver {
    // 返回当前签名密钥，密钥可随配置轮换。
    Mono<String> resolveSigningKey();
}
