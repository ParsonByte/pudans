package cn.tolim.pudans.security.authentication;

import org.springframework.security.authentication.AccountExpiredException;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UserDetails;
import reactor.core.publisher.Mono;


public final class UserAccountStatusChecker {

    private UserAccountStatusChecker() {}

    public static Mono<Void> check(UserDetails user) {
        if (!user.isAccountNonLocked()) {
            return Mono.error(new LockedException("用户账户已被锁定"));
        }
        if (!user.isEnabled()) {
            return Mono.error(new DisabledException("用户已被禁用"));
        }
        if (!user.isAccountNonExpired()) {
            return Mono.error(new AccountExpiredException("用户账户已过期"));
        }
        if (!user.isCredentialsNonExpired()) {
            return Mono.error(new CredentialsExpiredException("用户凭据已过期"));
        }
        return Mono.empty();
    }
}
