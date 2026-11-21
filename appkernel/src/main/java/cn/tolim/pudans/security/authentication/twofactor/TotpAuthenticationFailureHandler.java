package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.core.risk.RiskDecision;
import cn.tolim.pudans.core.risk.RiskScene;
import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.infra.utils.IpAddressUtils;
import cn.tolim.pudans.security.authentication.exception.TooManyRequestsException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TotpAuthenticationFailureHandler implements ServerAuthenticationFailureHandler {

    static final String INVALID_TOTP_CODE = "invalid-totp-code";

    private final AuditLog auditLog;

    private final RiskGuard riskGuard;

    private final DomainEventPublisher domainEventPublisher;

    private final ServerResponse.Context context;

    // 优先使用安全上下文中的用户名，缺失时回退审计属性。
    private static Mono<String> currentUsername(ServerWebExchange exchange) {
        var attribute = (String) exchange.getAttributes().get(AuditLog.USERNAME_ATTRIBUTE);
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null && authentication.isAuthenticated())
                .map(Authentication::getName)
                .defaultIfEmpty(attribute == null ? "anonymous" : attribute);
    }

    // 处于风控封禁期时直接返回限流响应，不再校验验证码。
    private static Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var body = "{\"type\":\"https://pudans.tolim.cn/probs/risk-blocked\","
                + "\"title\":\"请求过于频繁\",\"status\":429,"
                + "\"detail\":\"TOTP 验证尝试次数过多。\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    // 以会话标识作为风控主体，无会话时退化为客户端地址。
    private static Mono<String> subject(ServerWebExchange exchange) {
        return exchange.getSession()
                .map(session -> session.getId())
                .defaultIfEmpty(IpAddressUtils.getClientIp(exchange.getRequest()));
    }

    // 先记录失败审计，再依据风控结论决定限流或返回无效码。
    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange webFilterExchange, AuthenticationException exception) {
        var exchange = webFilterExchange.getExchange();
        var rateLimited = exception instanceof TooManyRequestsException;
        return auditFailure(exchange, exception)
                .then(rateLimited ? Mono.just(true) : escalate(exchange))
                .flatMap(blocked -> blocked
                        ? tooManyRequests(exchange)
                        : ProblemDetailResponses.write(
                                exchange,
                                context,
                                HttpStatus.UNAUTHORIZED,
                                INVALID_TOTP_CODE,
                                "TOTP 验证码无效。"));
    }

    // 对失败尝试做风险评估，命中阈值时执行封禁并记录事件。
    private Mono<Boolean> escalate(ServerWebExchange exchange) {
        return subject(exchange)
                .flatMap(subject -> riskGuard
                        .evaluate(RiskScene.TOTP_ATTEMPT, subject)
                        .flatMap(decision -> decision.allowed()
                                ? Mono.just(false)
                                : handleBreach(exchange, subject, decision)));
    }

    // 按风控结论封禁主体并写入审计，标记该请求已被拦截。
    private Mono<Boolean> handleBreach(ServerWebExchange exchange, String subject, RiskDecision decision) {
        var ban = decision.blockFor().isZero()
                ? Mono.empty()
                : riskGuard.ban(RiskScene.TOTP_ATTEMPT, subject, decision.blockFor());
        var context = new LinkedHashMap<String, Object>();
        context.put("scene", RiskScene.TOTP_ATTEMPT);
        context.put("subject", subject);
        context.put("count", decision.count());
        context.put("blockSeconds", decision.blockFor().toSeconds());
        return ban
                .then(auditLog.recordChild(
                        exchange,
                        null,
                        "RISK_BLOCKED",
                        "RiskControl",
                        RiskScene.TOTP_ATTEMPT,
                        "DENIED",
                        context,
                        null))
                .thenReturn(true);
    }

    // 记录验证码失败审计，同时发布登录失败领域事件。
    private Mono<Void> auditFailure(ServerWebExchange exchange, AuthenticationException exception) {
        return currentUsername(exchange).flatMap(username -> {
            var context = new LinkedHashMap<String, Object>();
            context.put("username", username);
            context.put("method", "totp");
            context.put("reason", exception.getClass().getSimpleName());
            publishLoginFailure(exchange, username, exception);
            return auditLog.recordChild(
                    exchange,
                    null,
                    "TOTP_LOGIN_FAILED",
                    "User",
                    username,
                    "DENIED",
                    context,
                    null);
        });
    }

    // 发布登录失败事件，供风控与通知等下游消费者使用。
    private void publishLoginFailure(
            ServerWebExchange exchange, String username, AuthenticationException exception) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("username", username);
        payload.put("method", "two-factor");
        payload.put("reason", exception.getClass().getSimpleName());
        payload.put("ip", IpAddressUtils.getClientIp(exchange.getRequest()));
        domainEventPublisher.publish(
                "user.login.failed",
                payload,
                (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE));
    }
}
