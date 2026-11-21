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

    // 注入验证方式、账户查询与风险控制依赖
    SudoChallenges(
            List<SudoVerificationProvider> providers,
            Accounts accounts,
            RiskGuard riskGuard) {
        this.providers = providers;
        this.accounts = accounts;
        this.riskGuard = riskGuard;
    }

    // 判断是否为令牌认证，用于区分会话场景
    static boolean isJwtAuthentication(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken;
    }

    // 会话认证排除匿名与令牌认证
    static boolean isSessionAuthentication(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && !isJwtAuthentication(authentication);
    }

    // 替换时钟以便测试过期判定
    void setClock(Clock clock) {
        Assert.notNull(clock, "时钟不能为空");
        this.clock = clock;
    }

    // 查询当前会话的确认状态与可用验证方式
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

    // 按验证方式下发验证码，并先经过风险校验
    Mono<Void> sendCode(String method) {
        return requireSessionAuthentication()
                .flatMap(auth -> invoke(
                        auth.getName(),
                        RiskScene.SUDO_SEND,
                        method,
                        (user, provider) -> provider.sendCode(user)));
    }

    // 校验通过后开启确认时段并记录操作日志
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

    // 统一执行风险校验、用户加载与验证方式调度
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

    // 未激活时抛出所需方式，无方式可用则放行
    Mono<Void> requireSudo(ServerWebExchange exchange, String username) {
        return requireSudo(exchange, username, true);
    }

    // 无可用验证方式时，由调用方决定是否允许继续，令牌管理必须拒绝。
    Mono<Void> requireSudo(ServerWebExchange exchange, String username, boolean allowWithoutMethods) {
        return isActive(exchange, username).flatMap(active -> {
            if (active) {
                return Mono.empty();
            }
            return availableMethods(username)
                    .onErrorResume(UserNotFoundException.class, ignored -> Mono.just(List.of()))
                    .flatMap(methods -> {
                        if (methods.isEmpty()) {
                            return allowWithoutMethods
                                    ? Mono.empty()
                                    : Mono.error(new SudoRequiredException(List.of()));
                        }
                        var names = methods.stream().map(SudoMethod::name).toList();
                        return Mono.error(new SudoRequiredException(names));
                    });
        });
    }

    // 仅同一用户的未过期确认才算激活
    Mono<Boolean> isActive(ServerWebExchange exchange, String username) {
        return expiresAt(exchange)
                .flatMap(expiresAt -> sudoUsername(exchange)
                        .map(sudoUsername -> sudoUsername.equals(username)
                                && expiresAt.isAfter(clock.instant())))
                .defaultIfEmpty(false);
    }

    // 读取会话中记录的安全确认用户名
    private Mono<String> sudoUsername(ServerWebExchange exchange) {
        return exchange.getSession()
                .mapNotNull(session -> session.getAttribute(SESSION_USERNAME_ATTRIBUTE));
    }

    // 汇总指定用户当前可用的验证方式
    Mono<List<SudoMethod>> availableMethods(String username) {
        return accounts.getUser(username).flatMapMany(this::availableMethods).collectList();
    }

    // 将支持的验证方式转换为对外描述
    Flux<SudoMethod> availableMethods(User user) {
        return supportedProviders(user)
                .map(provider ->
                        new SudoMethod(provider.method(), provider.canSendCode(), provider.maskedTarget(user)));
    }

    // 过滤出适用于该用户的验证方式实现
    private Flux<SudoVerificationProvider> supportedProviders(User user) {
        return Flux.fromIterable(providers).filterWhen(provider -> provider.supports(user));
    }

    // 仅允许浏览器会话访问安全确认接口
    Mono<Authentication> requireSessionAuthentication() {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(SudoChallenges::isSessionAuthentication)
                .switchIfEmpty(Mono.error(() -> new AccessDeniedException("Sudo API 需要浏览器会话")));
    }

    // 解析指定验证方式，缺失时拒绝请求
    private Mono<SudoVerificationProvider> requireSupported(User user, String method) {
        return supportedProviders(user)
                .filter(provider -> provider.method().equals(method))
                .next()
                .switchIfEmpty(Mono.error(() -> new ServerWebInputException("Sudo 验证方式不可用")));
    }

    // 在会话中记录确认用户与过期时间
    private Mono<Void> activate(ServerWebExchange exchange, String username) {


        var expiresAt = clock.instant().plus(SUDO_TTL);
        return exchange.getSession()
                .doOnNext(session -> {
                    session.getAttributes().put(SESSION_ATTRIBUTE, expiresAt.toEpochMilli());
                    session.getAttributes().put(SESSION_USERNAME_ATTRIBUTE, username);
                })
                .then();
    }

    // 从会话读取安全确认的过期时间
    private Mono<Instant> expiresAt(ServerWebExchange exchange) {
        return exchange.getSession()
                .mapNotNull(session -> session.getAttribute(SESSION_ATTRIBUTE))
                .filter(Number.class::isInstance)
                .cast(Number.class)
                .map(number -> Instant.ofEpochMilli(number.longValue()));
    }
}
