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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;


@RequiredArgsConstructor
class LoginReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    private final ReactiveUserDetailsService userDetailsService;

    private final Accounts accounts;

    private final PasswordEncoder passwordEncoder;

    private final ReactiveUserDetailsPasswordService passwordService;

    private Scheduler scheduler = Schedulers.boundedElastic();


    void setScheduler(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

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


    Mono<UserDetails> lookupByUsername(String loginId) {
        return userDetailsService.findByUsername(loginId);
    }


    Mono<UserDetails> lookupByEmail(String loginId) {
        if (!loginId.contains("@")) {
            return Mono.empty();
        }
        return accounts
                .findUserByVerifiedEmail(loginId)
                .flatMap(user ->
                        userDetailsService.findByUsername(user.getMetadata().getName()));
    }

    private Mono<UserDetails> upgradePasswordIfNeeded(UserDetails userDetails, String password) {
        if (passwordService != null) {
            var upgradeEncoding =
                    userDetails.getPassword() != null && passwordEncoder.upgradeEncoding(userDetails.getPassword());
            if (upgradeEncoding) {
                var newPassword = passwordEncoder.encode(password);
                return passwordService.updatePassword(userDetails, newPassword);
            }
        }
        return Mono.just(userDetails);
    }
}
