package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.core.risk.RiskScene;
import cn.tolim.pudans.infra.exception.AccessDeniedException;
import cn.tolim.pudans.infra.exception.RateLimitExceededException;
import cn.tolim.pudans.infra.exception.UserNotFoundException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.BiFunction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Component
class SudoChallenges {

    static final String SESSION_ATTRIBUTE = "pudans.sudo.expiresAt";
    static final String SESSION_USERNAME_ATTRIBUTE = "pudans.sudo.username";
    static final Duration SUDO_TTL = Duration.ofMinutes(30);
    private final List<SudoVerificationProvider> providers;
    private final Accounts accounts;
    private final RiskGuard riskGuard;

    private Clock clock = Clock.systemUTC();

    SudoChallenges(
            List<SudoVerificationProvider> providers,
            Accounts accounts,
            RiskGuard riskGuard) {
        this.providers = providers;
        this.accounts = accounts;
        this.riskGuard = riskGuard;
    }

    static boolean isJwtAuthentication(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken;
    }

    static boolean isSessionAuthentication(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !isJwtAuthentication(authentication);
    }

    void setClock(Clock clock) {
        Assert.notNull(clock, "时钟不能为空");
        this.clock = clock;
    }

    Mono<SudoStatus> status(ServerWebExchange exchange) {
        return requireSessionAuthentication()
                .flatMap(auth -> Mono.zip(
                                availableMethods(auth.getName()),
                                expiresAt(exchange).defaultIfEmpty(Instant.EPOCH),
                                sudoUsername(exchange).defaultIfEmpty(""))
                        .map(tuple -> {
                            var methods = tuple.getT1();
                            var expiresAt = tuple.getT2();
                            var sudoUsername = tuple.getT3();
                            var active = sudoUsername.equals(auth.getName())
                                    && expiresAt.isAfter(clock.instant());
                            return new SudoStatus(active, active ? expiresAt : null, methods);
                        }));
    }

    Mono<Void> sendCode(String method) {
        return requireSessionAuthentication()
                .flatMap(auth -> invoke(
                        auth.getName(),
                        RiskScene.SUDO_SEND,
                        method,
                        (user, provider) -> provider.sendCode(user)));
    }

    Mono<Void> confirm(String method, String code, ServerWebExchange exchange) {
        return requireSessionAuthentication()
                .flatMap(auth -> invoke(
                        auth.getName(),
                        RiskScene.SUDO_CONFIRM,
                        method,
                        (user, provider) -> provider.verify(user, code))
                        .then(Mono.defer(() -> activate(exchange, auth.getName())))
                        .doOnSuccess(
                                unused -> log.info("用户 '{}' 已通过 '{}' 完成安全确认", auth.getName(), method))
                        .doOnError(error ->
                                log.info("用户 '{}' 通过 '{}' 的安全确认失败", auth.getName(), method)));
    }

    private <T> Mono<T> invoke(
            String username,
            String scene,
            String method,
            BiFunction<User, SudoVerificationProvider, Mono<T>> action) {
        return riskGuard
                .evaluate(scene, username)
                .flatMap(decision -> decision.allowed()
                        ? Mono.just(username)
                        : Mono.error(new RateLimitExceededException(null)))
                .flatMap(accounts::getUser)
                .flatMap(user -> requireSupported(user, method)
                        .flatMap(provider -> Mono.defer(() -> action.apply(user, provider))));
    }

    Mono<Void> requireSudo(ServerWebExchange exchange, String username) {
        return isActive(exchange, username).flatMap(active -> {
            if (active) {
                return Mono.empty();
            }
            return availableMethods(username)
                    .onErrorResume(UserNotFoundException.class, ignored -> Mono.just(List.of()))
                    .flatMap(methods -> {
                        if (methods.isEmpty()) {
                            return Mono.empty();
                        }
                        var names = methods.stream().map(SudoMethod::name).toList();
                        return Mono.error(new SudoRequiredException(names));
                    });
        });
    }

    Mono<Boolean> isActive(ServerWebExchange exchange, String username) {
        return expiresAt(exchange)
                .flatMap(expiresAt -> sudoUsername(exchange)
                        .map(sudoUsername -> sudoUsername.equals(username)
                                && expiresAt.isAfter(clock.instant())))
                .defaultIfEmpty(false);
    }

    private Mono<String> sudoUsername(ServerWebExchange exchange) {
        return exchange.getSession()
                .mapNotNull(session -> session.getAttribute(SESSION_USERNAME_ATTRIBUTE));
    }

    Mono<List<SudoMethod>> availableMethods(String username) {
        return accounts.getUser(username).flatMapMany(this::availableMethods).collectList();
    }

    Flux<SudoMethod> availableMethods(User user) {
        return supportedProviders(user)
                .map(provider ->
                        new SudoMethod(provider.method(), provider.canSendCode(), provider.maskedTarget(user)));
    }

    private Flux<SudoVerificationProvider> supportedProviders(User user) {
        return Flux.fromIterable(providers).filterWhen(provider -> provider.supports(user));
    }

    Mono<Authentication> requireSessionAuthentication() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(SudoChallenges::isSessionAuthentication)
                .switchIfEmpty(Mono.error(() -> new AccessDeniedException("Sudo API 需要浏览器会话")));
    }

    private Mono<SudoVerificationProvider> requireSupported(User user, String method) {
        return supportedProviders(user)
                .filter(provider -> provider.method().equals(method))
                .next()
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("Sudo 验证方式不可用")));
    }

    private Mono<Void> activate(ServerWebExchange exchange, String username) {


        var expiresAt = clock.instant().plus(SUDO_TTL);
        return exchange.getSession()
                .doOnNext(session -> {
                    session.getAttributes().put(SESSION_ATTRIBUTE, expiresAt.toEpochMilli());
                    session.getAttributes().put(SESSION_USERNAME_ATTRIBUTE, username);
                })
                .then();
    }

    private Mono<Instant> expiresAt(ServerWebExchange exchange) {
        return exchange.getSession()
                .mapNotNull(session -> session.getAttribute(SESSION_ATTRIBUTE))
                .filter(Number.class::isInstance)
                .cast(Number.class)
                .map(number -> Instant.ofEpochMilli(number.longValue()));
    }
}
