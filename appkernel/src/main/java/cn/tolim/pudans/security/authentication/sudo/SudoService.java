package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.core.risk.service.RiskControlService;
import cn.tolim.pudans.core.risk.service.RiskScene;
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
class SudoService {

    static final String SESSION_ATTRIBUTE = "pudans.sudo.expiresAt";
    static final Duration SUDO_TTL = Duration.ofMinutes(30);
    private final List<SudoVerificationProvider> providers;
    private final UserService userService;
    private final RiskControlService riskControlService;

    private Clock clock = Clock.systemUTC();

    SudoService(
            List<SudoVerificationProvider> providers,
            UserService userService,
            RiskControlService riskControlService) {
        this.providers = providers;
        this.userService = userService;
        this.riskControlService = riskControlService;
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
        Assert.notNull(clock, "Clock must not be null");
        this.clock = clock;
    }

    Mono<SudoStatus> status(ServerWebExchange exchange) {
        return requireSessionAuthentication()
                .flatMap(auth -> Mono.zip(
                        availableMethods(auth.getName()),
                        expiresAt(exchange).defaultIfEmpty(Instant.EPOCH),
                        (methods, expiresAt) -> {
                            var active = expiresAt.isAfter(clock.instant());
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
                        .then(Mono.defer(() -> activate(exchange)))
                        .doOnSuccess(
                                unused -> log.info("Sudo confirmed for user '{}' via '{}'", auth.getName(), method))
                        .doOnError(error ->
                                log.info("Sudo confirmation failed for user '{}' via '{}'", auth.getName(), method)));
    }

    private <T> Mono<T> invoke(
            String username,
            String scene,
            String method,
            BiFunction<User, SudoVerificationProvider, Mono<T>> action) {
        return riskControlService
                .evaluate(scene, username)
                .flatMap(decision -> decision.allowed()
                        ? Mono.just(username)
                        : Mono.error(new RateLimitExceededException(null)))
                .flatMap(userService::getUser)
                .flatMap(user -> requireSupported(user, method)
                        .flatMap(provider -> Mono.defer(() -> action.apply(user, provider))));
    }

    Mono<Void> requireSudo(ServerWebExchange exchange, String username) {
        return isActive(exchange).flatMap(active -> {
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

    Mono<Boolean> isActive(ServerWebExchange exchange) {
        return expiresAt(exchange)
                .map(expiresAt -> expiresAt.isAfter(clock.instant()))
                .defaultIfEmpty(false);
    }

    Mono<List<SudoMethod>> availableMethods(String username) {
        return userService.getUser(username).flatMapMany(this::availableMethods).collectList();
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
                .filter(SudoService::isSessionAuthentication)
                .switchIfEmpty(Mono.error(() -> new AccessDeniedException("Sudo APIs require a browser session")));
    }

    private Mono<SudoVerificationProvider> requireSupported(User user, String method) {
        return supportedProviders(user)
                .filter(provider -> provider.method().equals(method))
                .next()
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("Sudo method is not available")));
    }

    private Mono<Void> activate(ServerWebExchange exchange) {


        var expiresAt = clock.instant().plus(SUDO_TTL);
        return exchange.getSession()
                .doOnNext(session -> session.getAttributes().put(SESSION_ATTRIBUTE, expiresAt.toEpochMilli()))
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
