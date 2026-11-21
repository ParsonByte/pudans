package cn.tolim.pudans.core.security.risk;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.infra.event.DomainEventPublisher;
import cn.tolim.pudans.contract.risk.dto.RiskAlert;
import cn.tolim.pudans.core.risk.RiskGuard;
import cn.tolim.pudans.core.risk.RiskDecision;
import cn.tolim.pudans.core.risk.RiskRuleRegistry;
import cn.tolim.pudans.core.risk.RiskScene;
import cn.tolim.pudans.core.security.authorization.AuthorityUtils;

import cn.tolim.pudans.core.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.core.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.core.infra.feishu.FeishuAlertNotifier;
import cn.tolim.pudans.core.security.web.StaticRequestPaths;
import cn.tolim.pudans.core.infra.utils.IpAddressUtils;

@Slf4j
@Component
@Order(-103)
@RequiredArgsConstructor
public class RiskControlWebFilter implements WebFilter {

    private static final String LOGIN_PATH = "/login";

    private static final String RISK_RESOURCE_TYPE = "RiskControl";

    private final RiskGuard riskGuard;

    private final RiskRuleRegistry riskRuleRegistry;

    private final ServerSecurityContextRepository securityContextRepository;

    private final ReactiveExtensionClient client;

    private final AuditLog auditLog;

    private final FeishuAlertNotifier alertNotifier;

    private final DomainEventPublisher domainEventPublisher;

    private final ServerResponse.Context context;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (StaticRequestPaths.isStatic(exchange)
                || ProblemDetailResponses.TOO_MANY_REQUESTS_PATH.equals(
                        exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        return actor(exchange).flatMap(actor -> {
            if (actor.superAdmin()) {
                return chain.filter(exchange);
            }
            var ip = IpAddressUtils.getClientIp(exchange.getRequest());
            return preCheck(exchange, ip)
                    .flatMap(pre -> pre.allowed()
                            ? observe(exchange, ip, chain)
                            : blocked(exchange, ip, pre));
        });
    }

    private Mono<Actor> actor(ServerWebExchange exchange) {
        return securityContextRepository
                .load(exchange)
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null
                        && authentication.isAuthenticated()
                        && !(authentication instanceof AnonymousAuthenticationToken))
                .map(authentication -> {
                    exchange.getAttributes().put(AuditLog.USERNAME_ATTRIBUTE, authentication.getName());
                    var roles = AuthorityUtils.authoritiesToRoles(authentication.getAuthorities());
                    return new Actor(AuthorityUtils.containsSuperRole(roles));
                })
                .defaultIfEmpty(Actor.anonymous())
                .onErrorResume(error -> {
                    log.debug("无法读取安全上下文，按未登录请求处理", error);
                    return Mono.just(Actor.anonymous());
                });
    }

    private Mono<PreCheck> preCheck(ServerWebExchange exchange, String ip) {
        var path = exchange.getRequest().getPath().value();
        var banScenes = new java.util.LinkedHashSet<String>();
        banScenes.add(RiskScene.REQUEST_FLOOD);
        if (LOGIN_PATH.equals(path)) {
            banScenes.add(RiskScene.LOGIN_FAILURE);
        }
        banScenes.addAll(riskRuleRegistry.enabledScenes());
        return riskGuard
                .firstBannedScene(banScenes, ip)
                .map(scene -> new PreCheck(
                        false, scene, new RiskDecision(false, 0L, Duration.ZERO, Duration.ZERO)))
                .switchIfEmpty(Mono.defer(() -> riskGuard
                        .evaluate(RiskScene.REQUEST_FLOOD, ip)
                        .map(decision -> decision.allowed()
                                ? PreCheck.pass()
                                : new PreCheck(false, RiskScene.REQUEST_FLOOD, decision))));
    }

    private Mono<Void> observe(ServerWebExchange exchange, String ip, WebFilterChain chain) {
        return chain.filter(exchange)
                .then(Mono.defer(() -> after(exchange, ip, null)))
                .onErrorResume(error -> after(exchange, ip, error).then(Mono.error(error)));
    }

    private Mono<Void> after(ServerWebExchange exchange, String ip, Throwable error) {
        var status = exchange.getResponse().getStatusCode();
        var code = status != null ? status.value() : error != null ? 500 : 200;
        var path = exchange.getRequest().getPath().value();
        var checks = new ArrayList<Mono<Void>>();
        if (LOGIN_PATH.equals(path) && code == 401) {
            checks.add(recordFailure(RiskScene.LOGIN_FAILURE, ip, exchange));
        }
        if (code == 401 || code == 403) {
            checks.add(recordFailure(RiskScene.AUTH_FAILURE, ip, exchange));
        }
        if (code >= 500) {
            checks.add(recordFailure(RiskScene.SERVER_ERROR, ip, exchange));
        }
        return Flux.concat(checks).then();
    }

    private Mono<Void> recordFailure(String scene, String ip, ServerWebExchange exchange) {
        return riskGuard
                .evaluate(scene, ip)
                .flatMap(decision -> decision.allowed() ? Mono.empty() : escalate(scene, ip, decision, exchange))
                .onErrorResume(error -> {
                    log.warn("无法评估风险场景 [{}]", scene, error);
                    return Mono.empty();
                });
    }

    private Mono<Void> escalate(String scene, String ip, RiskDecision decision, ServerWebExchange exchange) {
        var banned = !decision.blockFor().isZero();
        var ban = banned ? riskGuard.ban(scene, ip, decision.blockFor()) : Mono.empty();
        return ban.then(banned
                        ? audit(exchange, ip, scene, decision, "DENIED", "RISK_BLOCKED")
                        : Mono.empty())
                .then(riskGuard
                        .markAlerted(
                                scene,
                                ip,
                                decision.retryAfter().isZero()
                                        ? Duration.ofMinutes(5)
                                        : decision.retryAfter())
                        .flatMap(first -> {
                            if (!Boolean.TRUE.equals(first)) {
                                return Mono.empty();
                            }
                            notifyAlert(scene, ip, decision, exchange);
                            return audit(exchange, ip, scene, decision, "FAILURE", "RISK_ALERT");
                        }));
    }

    private Mono<Void> blocked(ServerWebExchange exchange, String ip, PreCheck pre) {
        var decision = pre.decision();
        var ban = decision.blockFor().isZero()
                ? Mono.empty()
                : riskGuard.ban(pre.scene(), ip, decision.blockFor());
        return ban.then(audit(exchange, ip, pre.scene(), decision, "DENIED", "RISK_BLOCKED"))
                .then(riskGuard
                        .markAlerted(
                                pre.scene(),
                                ip,
                                decision.retryAfter().isZero() ? Duration.ofMinutes(5) : decision.retryAfter())
                        .flatMap(first -> {
                            if (!Boolean.TRUE.equals(first)) {
                                return Mono.empty();
                            }
                            notifyAlert(pre.scene(), ip, decision, exchange);
                            return audit(exchange, ip, pre.scene(), decision, "FAILURE", "RISK_ALERT");
                        }))
                .then(Mono.fromRunnable(() -> publishBlocked(exchange, ip, pre.scene(), decision)))
                .then(reject(exchange, decision));
    }

    private Mono<Void> audit(
            ServerWebExchange exchange,
            String ip,
            String scene,
            RiskDecision decision,
            String result,
            String operation) {
        return actorIdentity(exchange).flatMap(identity -> Mono.fromRunnable(() -> {
            var traceId = AuditLog.traceId(exchange);
            exchange.getResponse().getHeaders().set(AuditLog.TRACE_ID_HEADER, traceId);
            var traceFlowId = (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE);
            var syntheticFlow = traceFlowId == null || traceFlowId.isBlank();
            if (syntheticFlow) {
                traceFlowId = AuditIdGenerator.next();
                exchange.getAttributes().put(AuditLog.TRACE_FLOW_ID_ATTRIBUTE, traceFlowId);
            }
            var parentAuditId = (String) exchange.getAttributes().get(AuditLog.AUDIT_ID_ATTRIBUTE);
            var record = auditLog.newRecord(traceId, identity.uid(), operation);
            record.setTraceFlowId(traceFlowId);
            if (syntheticFlow) {
                record.markSyntheticFlow();
            }
            record.setParentAuditId(parentAuditId);
            record.setResourceType(RISK_RESOURCE_TYPE);
            record.setResourceName(scene);
            record.setRequestMethod(exchange.getRequest().getMethod().name());
            record.setRequestPath(exchange.getRequest().getPath().value());
            record.setIp(ip);
            record.setUserAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"));
            record.setResult(result);
            record.setDurationMs(0L);
            record.setTimestamp(Instant.now());
            var context = context(scene, ip, decision);
            if (StringUtils.hasText(identity.username())) {
                context.put("username", identity.username());
            }
            record.setContext(context);
            record.setActionSnapshot(JsonUtils.objectToJson(context));
            auditLog.record(record);
        }));
    }

    private Mono<ActorIdentity> actorIdentity(ServerWebExchange exchange) {
        var uid = (String) exchange.getAttributes().get(AuditLog.UID_ATTRIBUTE);
        var username = (String) exchange.getAttributes().get(AuditLog.USERNAME_ATTRIBUTE);
        if (StringUtils.hasText(uid)) {
            return Mono.just(new ActorIdentity(uid, StringUtils.hasText(username) ? username : null));
        }
        if (!StringUtils.hasText(username)) {
            return Mono.just(new ActorIdentity("anonymous", null));
        }
        return client.fetch(User.class, username)
                .map(user -> {
                    var userId = user.getSpec() == null ? null : user.getSpec().getUid();
                    return new ActorIdentity(
                            StringUtils.hasText(userId) ? userId : username, username);
                })
                .doOnNext(identity ->
                        exchange.getAttributes().put(AuditLog.UID_ATTRIBUTE, identity.uid()))
                .defaultIfEmpty(new ActorIdentity(username, username))
                .onErrorResume(error -> {
                    log.warn("无法解析风险事件触发用户 [{}]", username, error);
                    return Mono.just(new ActorIdentity(username, username));
                });
    }

    private Map<String, Object> context(String scene, String ip, RiskDecision decision) {
        var context = new LinkedHashMap<String, Object>();
        context.put("scene", scene);
        context.put("subject", ip);
        context.put("count", decision.count());
        context.put("retryAfterMillis", decision.retryAfter().toMillis());
        context.put("blockSeconds", decision.blockFor().toSeconds());
        return context;
    }

    private void notifyAlert(String scene, String ip, RiskDecision decision, ServerWebExchange exchange) {
        var alert = new RiskAlert(
                scene, ip, decision.count(), exchange.getRequest().getPath().value(), Instant.now());
        var payload = new LinkedHashMap<String, Object>();
        payload.put("scene", scene);
        payload.put("subject", ip);
        payload.put("count", decision.count());
        payload.put("path", exchange.getRequest().getPath().value());
        var username = (String) exchange.getAttributes().get(AuditLog.USERNAME_ATTRIBUTE);
        if (StringUtils.hasText(username)) {
            payload.put("username", username);
        }
        domainEventPublisher.publish(
                "risk.alert",
                payload,
                (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE));
        alertNotifier.send(alert).subscribe();
    }

    private void publishBlocked(
            ServerWebExchange exchange, String ip, String scene, RiskDecision decision) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("scene", scene);
        payload.put("subject", ip);
        payload.put("count", decision.count());
        payload.put("path", exchange.getRequest().getPath().value());
        payload.put("blockSeconds", decision.blockFor().toSeconds());
        var username = (String) exchange.getAttributes().get(AuditLog.USERNAME_ATTRIBUTE);
        if (StringUtils.hasText(username)) {
            payload.put("username", username);
        }
        domainEventPublisher.publish(
                "risk.blocked",
                payload,
                (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE));
    }

    private Mono<Void> reject(ServerWebExchange exchange, RiskDecision decision) {
        if (!decision.retryAfter().isZero()) {
            exchange.getResponse().getHeaders().set("Retry-After", String.valueOf(decision.retryAfter().toSeconds()));
        }
        return ProblemDetailResponses.writeOrRedirect(
                exchange,
                context,
                HttpStatus.TOO_MANY_REQUESTS,
                "risk-blocked",
                "请求已被风险控制拦截。",
                ProblemDetailResponses.TOO_MANY_REQUESTS_PATH);
    }

    record PreCheck(boolean allowed, String scene, RiskDecision decision) {

        static PreCheck pass() {
            return new PreCheck(true, null, RiskDecision.permit());
        }
    }

    record Actor(boolean superAdmin) {

        static Actor anonymous() {
            return new Actor(false);
        }
    }

    record ActorIdentity(String uid, String username) {}
}
