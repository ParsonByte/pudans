package cn.tolim.pudans.security.authentication.rememberme;

import cn.tolim.pudans.security.contract.authentication.LoginCipher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
class DefaultCookieSignatureKeyResolver implements CookieSignatureKeyResolver {

    private final LoginCipher loginCipher;

    @Override
    public Mono<String> resolveSigningKey() {
        return Mono.fromSupplier(loginCipher::getKeyId);
    }
}
