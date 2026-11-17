package cn.tolim.pudans.security.authentication.twofactor;

import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.core.risk.service.RiskControlService;
import cn.tolim.pudans.core.risk.service.RiskDecision;
import cn.tolim.pudans.core.risk.service.RiskScene;
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

    private final AuditService auditService;

    private final RiskControlService riskControlService;

    private final DomainEventPublisher domainEventPublisher;

    private final ServerResponse.Context context;

    private static Mono<String> currentUsername(ServerWebExchange exchange) {
        var attribute = (String) exchange.getAttributes().get(AuditService.USERNAME_ATTRIBUTE);
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null && authentication.isAuthenticated())
                .map(Authentication::getName)
                .defaultIfEmpty(attribute == null ? "anonymous" : attribute);
    }

    private static Mono<Void> tooManyRequests(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var body = "{\"type\":\"https://pudans.tolim.cn/probs/risk-blocked\","
                + "\"title\":\"请求过于频繁\",\"status\":429,"
                + "\"detail\":\"TOTP 验证尝试次数过多。\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private static Mono<String> subject(ServerWebExchange exchange) {
        return exchange.getSession()
                .map(session -> session.getId())
                .defaultIfEmpty(IpAddressUtils.getClientIp(exchange.getRequest()));
    }

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

    private Mono<Boolean> escalate(ServerWebExchange exchange) {
        return subject(exchange)
                .flatMap(subject -> riskControlService
                        .evaluate(RiskScene.TOTP_ATTEMPT, subject)
                        .flatMap(decision -> decision.allowed()
                                ? Mono.just(false)
                                : handleBreach(exchange, subject, decision)));
    }

    private Mono<Boolean> handleBreach(ServerWebExchange exchange, String subject, RiskDecision decision) {
        var ban = decision.blockFor().isZero()
                ? Mono.empty()
                : riskControlService.ban(RiskScene.TOTP_ATTEMPT, subject, decision.blockFor());
        var context = new LinkedHashMap<String, Object>();
        context.put("scene", RiskScene.TOTP_ATTEMPT);
        context.put("subject", subject);
        context.put("count", decision.count());
        context.put("blockSeconds", decision.blockFor().toSeconds());
        return ban
                .then(auditService.recordChild(
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

    private Mono<Void> auditFailure(ServerWebExchange exchange, AuthenticationException exception) {
        return currentUsername(exchange).flatMap(username -> {
            var context = new LinkedHashMap<String, Object>();
            context.put("username", username);
            context.put("method", "totp");
            context.put("reason", exception.getClass().getSimpleName());
            publishLoginFailure(exchange, username, exception);
            return auditService.recordChild(
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
                (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE));
    }
}
