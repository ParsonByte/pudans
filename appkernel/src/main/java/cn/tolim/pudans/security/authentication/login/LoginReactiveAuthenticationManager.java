package cn.tolim.pudans.security.authentication.login;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.security.authentication.UserAccountStatusChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.ReactiveUserDetailsPasswordService;
import org.springframework.security.core.userdetails.ReactiveUserDetailsService;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;


@RequiredArgsConstructor
class LoginReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final ReactiveUserDetailsService reactiveUserDetailsService;

    private final Accounts accounts;

    private final PasswordEncoder passwordEncoder;

    private final ReactiveUserDetailsPasswordService reactiveUserDetailsPasswordService;

    private Scheduler scheduler = Schedulers.boundedElastic();


    // 允许测试替换查找用户所用的调度器。
    void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    // 按邮箱或用户名匹配密码，随后复核状态并升级哈希。
    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
        var loginId = authentication.getName();
        var credentials = authentication.getCredentials();
        var password = credentials != null ? credentials.toString() : null;


        return Flux.concat(Mono.defer(() -> lookupByEmail(loginId)), Mono.defer(() -> lookupByUsername(loginId)))
                .publishOn(scheduler)
                .filter(userDetails -> password != null && passwordEncoder.matches(password, userDetails.getPassword()))
                .next()
                .switchIfEmpty(Mono.error(() -> new BadCredentialsException("用户名或密码错误。")))
                .delayUntil(UserAccountStatusChecker::check)
                .flatMap(userDetails -> upgradePasswordIfNeeded(userDetails, password))
                .map(userDetails -> UsernamePasswordAuthenticationToken.authenticated(
                        userDetails, userDetails.getPassword(), userDetails.getAuthorities()));
    }


    // 按登录名查找用户详情。
    Mono<UserDetails> lookupByUsername(String loginId) {
        if (!StringUtils.hasText(loginId)) {
            return Mono.empty();
        }
        return accounts
                .findUserByLoginName(loginId)
                .flatMap(user -> reactiveUserDetailsService.findByUsername(user.getMetadata().getName()));
    }


    // 仅在登录名形如邮箱时按已验证邮箱查找。
    Mono<UserDetails> lookupByEmail(String loginId) {
        if (!loginId.contains("@")) {
            return Mono.empty();
        }
        return accounts
                .findUserByVerifiedEmail(loginId)
                .flatMap(user ->
                        reactiveUserDetailsService.findByUsername(user.getMetadata().getName()));
    }

    // 密码哈希编码过旧时自动升级存储值。
    private Mono<UserDetails> upgradePasswordIfNeeded(UserDetails userDetails, String password) {
        if (reactiveUserDetailsPasswordService != null) {
            var upgradeEncoding =
                    userDetails.getPassword() != null && passwordEncoder.upgradeEncoding(userDetails.getPassword());
            if (upgradeEncoding) {
                var newPassword = passwordEncoder.encode(password);
                return reactiveUserDetailsPasswordService.updatePassword(userDetails, newPassword);
            }
        }
        return Mono.just(userDetails);
    }
}
