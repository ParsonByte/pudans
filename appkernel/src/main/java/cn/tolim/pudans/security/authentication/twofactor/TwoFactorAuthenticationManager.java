package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import cn.tolim.pudans.security.authentication.twofactor.totp.TotpTwoFactorVerifier;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TwoFactorAuthenticationManager implements ReactiveAuthenticationManager {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final UserService userService;

    private final TotpTwoFactorVerifier totpVerifier;

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        if (!(authentication instanceof TwoFactorAuthenticationToken token)) {
            return Mono.error(new TwoFactorAuthException("不支持的双重验证令牌。"));
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(TwoFactorAuthentication.class::isInstance)
                .map(TwoFactorAuthentication.class::cast)
                .map(TwoFactorAuthentication::getPrevious)
                .switchIfEmpty(Mono.error(() -> new TwoFactorAuthException("需要完成多重身份验证。")))
                .flatMap(previous -> verify(token, previous));
    }

    private Mono<Authentication> verify(TwoFactorAuthenticationToken token, Authentication previous) {
        if (!(previous.getPrincipal() instanceof PudansUserDetailsContract)) {
            return Mono.error(new TwoFactorAuthException("无效的认证主体。"));
        }
        if (!TotpTwoFactorVerifier.TYPE.equalsIgnoreCase(token.getType())) {
            return Mono.error(new TwoFactorAuthException("不支持的双重验证类型 " + token.getType()));
        }
        return userService
                .getUser(previous.getName())
                .flatMap(user -> totpVerifier.verify(user, token.getCode()).timeout(TIMEOUT))
                .flatMap(valid -> {
                    if (!Boolean.TRUE.equals(valid)) {
                        return Mono.error(new TwoFactorAuthException("双重验证码无效。"));
                    }
                    if (previous instanceof CredentialsContainer container) {
                        container.eraseCredentials();
                    }
                    return Mono.just(previous);
                });
    }
}
