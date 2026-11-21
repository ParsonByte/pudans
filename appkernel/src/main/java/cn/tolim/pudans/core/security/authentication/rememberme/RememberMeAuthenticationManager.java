package cn.tolim.pudans.core.security.authentication.rememberme;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.RememberMeAuthenticationToken;
import org.springframework.security.core.Authentication;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RememberMeAuthenticationManager implements ReactiveAuthenticationManager {

    private static final String BAD_CREDENTIAL_MESSAGE = "提供的 RememberMe 令牌不包含预期的密钥";

    private final CookieSignatureKeyResolver cookieSignatureKeyResolver;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        return Mono.justOrEmpty(authentication)
                .filter(RememberMeAuthenticationToken.class::isInstance)
                .cast(RememberMeAuthenticationToken.class)
                .flatMap(this::doAuthenticate);
    }

    private Mono<Authentication> doAuthenticate(RememberMeAuthenticationToken token) {
        return cookieSignatureKeyResolver
                .resolveSigningKey()
                .filter(key -> key.hashCode() == token.getKeyHash())
                .switchIfEmpty(Mono.error(() -> new BadCredentialsException(BAD_CREDENTIAL_MESSAGE)))
                .thenReturn(token);
    }
}
