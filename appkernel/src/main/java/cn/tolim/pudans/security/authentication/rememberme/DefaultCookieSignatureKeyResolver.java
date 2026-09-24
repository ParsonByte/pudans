package cn.tolim.pudans.security.authentication.rememberme;

import cn.tolim.pudans.contract.security.authentication.CryptoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
class DefaultCookieSignatureKeyResolver implements CookieSignatureKeyResolver {

    private final CryptoService cryptoService;

    @Override
    public Mono<String> resolveSigningKey() {
        return Mono.fromSupplier(cryptoService::getKeyId);
    }
}
