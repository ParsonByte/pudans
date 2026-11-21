package cn.tolim.pudans.security.authentication.twofactor;

import static cn.tolim.pudans.security.authorization.AuthorityUtils.ROLE_PREFIX;
import static cn.tolim.pudans.security.authorization.AuthorityUtils.TWO_FACTOR_AUTHENTICATED_ROLE_NAME;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.security.authentication.PudansUserDetailsContract;
import cn.tolim.pudans.security.authentication.exception.TwoFactorAuthException;
import cn.tolim.pudans.security.authentication.twofactor.totp.TotpTwoFactorVerifier;
import java.time.Duration;
import java.util.ArrayList;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TwoFactorAuthenticationManager implements ReactiveAuthenticationManager {

    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final Accounts accounts;

    private final TotpTwoFactorVerifier totpVerifier;

    // 仅接受双重验证令牌，并从安全上下文取回登录前认证。
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

    // 校验验证码类型与主体后提升权限，成功后擦除旧凭据。
    private Mono<Authentication> verify(TwoFactorAuthenticationToken token, Authentication previous) {
        if (!(previous.getPrincipal() instanceof PudansUserDetailsContract)) {
            return Mono.error(new TwoFactorAuthException("无效的认证主体。"));
        }
        if (!TotpTwoFactorVerifier.TYPE.equalsIgnoreCase(token.getType())) {
            return Mono.error(new TwoFactorAuthException("不支持的双重验证类型 " + token.getType()));
        }
        return accounts
                .getUser(previous.getName())
                .flatMap(user -> totpVerifier.verify(user, token.getCode()).timeout(TIMEOUT))
                .flatMap(valid -> {
                    if (!Boolean.TRUE.equals(valid)) {
                        return Mono.error(new TwoFactorAuthException("双重验证码无效。"));
                    }
                    if (previous instanceof CredentialsContainer container) {
                        container.eraseCredentials();
                    }
                    var authorities = new ArrayList<GrantedAuthority>(previous.getAuthorities());
                    authorities.add(new SimpleGrantedAuthority(
                            ROLE_PREFIX + TWO_FACTOR_AUTHENTICATED_ROLE_NAME));
                    var elevated = UsernamePasswordAuthenticationToken.authenticated(
                            previous.getPrincipal(), previous.getCredentials(), authorities);
                    elevated.setDetails(previous.getDetails());
                    return Mono.just(elevated);
                });
    }
}
