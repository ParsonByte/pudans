package cn.tolim.pudans.security.risk;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.event.DomainEventPublisher;
import cn.tolim.pudans.contract.risk.RiskAlert;
import cn.tolim.pudans.core.risk.service.RiskAlertDispatcher;
import cn.tolim.pudans.core.risk.service.RiskControlService;
import cn.tolim.pudans.core.risk.service.RiskDecision;
import cn.tolim.pudans.core.risk.service.RiskRuleRegistry;
import cn.tolim.pudans.core.risk.service.RiskScene;

import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.utils.IpAddressUtils;

@Slf4j
@Component
@Order(-103)
@RequiredArgsConstructor
public class RiskControlWebFilter implements WebFilter {

    private static final String[] IGNORED_PREFIXES = {
        "/assets/",
        "/images/",
        "/js/",
        "/styles/",
        "/webjars/",
        "/favicon.ico"
    };

    private static final String LOGIN_PATH = "/login";

    private static final String RISK_RESOURCE_TYPE = "RiskControl";

    private final RiskControlService riskControlService;

    private final RiskRuleRegistry riskRuleRegistry;

    private final AuditService auditService;

    private final RiskAlertDispatcher alertDispatcher;

    private final DomainEventPublisher domainEventPublisher;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (ignored(exchange)) {
            return chain.filter(exchange);
        }
        var ip = IpAddressUtils.getClientIp(exchange.getRequest());
        return preCheck(exchange, ip)
                .flatMap(pre -> pre.allowed()
                        ? observe(exchange, ip, chain)
                        : blocked(exchange, ip, pre));
    }

    private Mono<PreCheck> preCheck(ServerWebExchange exchange, String ip) {
        var path = exchange.getRequest().getPath().value();
        var banScenes = new java.util.LinkedHashSet<String>();
        banScenes.add(RiskScene.REQUEST_FLOOD);
        if (LOGIN_PATH.equals(path)) {
            banScenes.add(RiskScene.LOGIN_FAILURE);
        }
        banScenes.addAll(riskRuleRegistry.enabledScenes());
        return riskControlService
                .firstBannedScene(banScenes, ip)
                .map(scene -> new PreCheck(
                        false, scene, new RiskDecision(false, 0L, Duration.ZERO, Duration.ZERO)))
                .switchIfEmpty(Mono.defer(() -> riskControlService
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
        return riskControlService
                .evaluate(scene, ip)
                .flatMap(decision -> decision.allowed() ? Mono.empty() : escalate(scene, ip, decision, exchange))
                .onErrorResume(error -> {
                    log.warn("Unable to evaluate risk scene [{}]", scene, error);
                    return Mono.empty();
                });
    }

    private Mono<Void> escalate(String scene, String ip, RiskDecision decision, ServerWebExchange exchange) {
        var ban = decision.blockFor().isZero()
                ? Mono.empty()
                : riskControlService.ban(scene, ip, decision.blockFor());
        return ban.then(audit(exchange, ip, scene, decision, "DENIED", "RISK_BLOCKED"))
                .then(riskControlService
                        .markAlerted(scene, ip, decision.retryAfter().isZero() ? Duration.ofMinutes(5) : decision.retryAfter())
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
                : riskControlService.ban(pre.scene(), ip, decision.blockFor());
        return ban.then(audit(exchange, ip, pre.scene(), decision, "DENIED", "RISK_BLOCKED"))
                .then(riskControlService
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
            ServerWebExchange exchange, String ip, String scene, RiskDecision decision, String result, String operation) {
        return Mono.fromRunnable(() -> {
            var traceId = AuditService.traceId(exchange);
            exchange.getResponse().getHeaders().set(AuditService.TRACE_ID_HEADER, traceId);
            var traceFlowId = (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE);
            var syntheticFlow = traceFlowId == null || traceFlowId.isBlank();
            if (syntheticFlow) {
                traceFlowId = AuditIdGenerator.next();
                exchange.getAttributes().put(AuditService.TRACE_FLOW_ID_ATTRIBUTE, traceFlowId);
            }
            var parentAuditId = (String) exchange.getAttributes().get(AuditService.AUDIT_ID_ATTRIBUTE);
            var uid = (String) exchange.getAttributes().get(AuditService.UID_ATTRIBUTE);
            var record = auditService.newRecord(traceId, uid == null ? "anonymous" : uid, operation);
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
            record.setContext(context);
            record.setActionSnapshot(JsonUtils.objectToJson(context));
            auditService.record(record);
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
        domainEventPublisher.publish(
                "risk.alert",
                payload,
                (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE));
        alertDispatcher.dispatch(alert);
    }

    private void publishBlocked(
            ServerWebExchange exchange, String ip, String scene, RiskDecision decision) {
        var payload = new LinkedHashMap<String, Object>();
        payload.put("scene", scene);
        payload.put("subject", ip);
        payload.put("count", decision.count());
        payload.put("path", exchange.getRequest().getPath().value());
        payload.put("blockSeconds", decision.blockFor().toSeconds());
        domainEventPublisher.publish(
                "risk.blocked",
                payload,
                (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE));
    }

    private Mono<Void> reject(ServerWebExchange exchange, RiskDecision decision) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        if (!decision.retryAfter().isZero()) {
            response.getHeaders().set("Retry-After", String.valueOf(decision.retryAfter().toSeconds()));
        }
        var body = "{\"type\":\"https://pudans.tolim.cn/probs/risk-blocked\","
                + "\"title\":\"Too Many Requests\",\"status\":429,"
                + "\"detail\":\"Request blocked by risk control.\"}";
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8))));
    }

    private static boolean ignored(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().value();
        for (var prefix : IGNORED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    record PreCheck(boolean allowed, String scene, RiskDecision decision) {

        static PreCheck pass() {
            return new PreCheck(true, null, RiskDecision.permit());
        }
    }
}
