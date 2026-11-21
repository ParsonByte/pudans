package cn.tolim.pudans.security.audit;

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
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.infra.trace.TraceContext;
import cn.tolim.pudans.contract.user.resource.User;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.security.web.StaticRequestPaths;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class AuditWebFilter implements WebFilter {

    private static final String AUDITED_ATTRIBUTE = AuditWebFilter.class.getName() + ".AUDITED";

    private static final int MAX_ID_LENGTH = 64;

    private final AuditLog auditLog;

    private final ReactiveExtensionClient client;

    private final AuditRecordRuleEvaluator ruleEvaluator;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (StaticRequestPaths.isStatic(exchange)) {
            return chain.filter(exchange);
        }
        
        
        if (exchange.getAttributes().putIfAbsent(AUDITED_ATTRIBUTE, Boolean.TRUE) != null) {
            return chain.filter(exchange);
        }
        var start = System.currentTimeMillis();
        var traceId = AuditLog.traceId(exchange);
        var inheritedFlowId = truncate(
                exchange.getRequest().getHeaders().getFirst(AuditLog.TRACE_FLOW_ID_HEADER), MAX_ID_LENGTH);
        var existingFlowId = (String) exchange.getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE);
        var traceFlowId = existingFlowId != null && !existingFlowId.isBlank()
                ? existingFlowId
                : inheritedFlowId == null || inheritedFlowId.isBlank()
                        ? cn.tolim.pudans.infra.audit.AuditIdGenerator.next()
                        : inheritedFlowId;
        var rootAuditId = cn.tolim.pudans.infra.audit.AuditIdGenerator.next();
        var trail = new cn.tolim.pudans.core.audit.AuditTrail(traceFlowId, rootAuditId);
        exchange.getAttributes().putIfAbsent(AuditLog.TRACE_FLOW_ID_ATTRIBUTE, traceFlowId);
        exchange.getAttributes().put(AuditLog.AUDIT_ID_ATTRIBUTE, rootAuditId);
        exchange.getAttributes().put(AuditLog.STEP_INDEX_ATTRIBUTE, 1);
        exchange.getAttributes().put(AuditLog.AUDIT_TRAIL_ATTRIBUTE, trail);
        var inheritedParent = truncate(
                exchange.getRequest().getHeaders().getFirst(AuditLog.PARENT_AUDIT_ID_HEADER), MAX_ID_LENGTH);
        if (inheritedParent != null && !inheritedParent.isBlank()) {
            exchange.getAttributes().put(AuditLog.PARENT_AUDIT_ID_ATTRIBUTE, inheritedParent);
        }
        exchange.getResponse().getHeaders().set(AuditLog.TRACE_ID_HEADER, traceId);
        exchange.getResponse().getHeaders().set(AuditLog.TRACE_FLOW_ID_HEADER, traceFlowId);
        return chain.filter(exchange)
                .contextWrite(context -> context.put(
                                cn.tolim.pudans.core.audit.AuditTrail.class, trail)
                        .put(TraceContext.TRACE_FLOW_ID_CONTEXT_KEY, traceFlowId))
                .then(Mono.defer(() -> write(exchange, start, null)))
                .onErrorResume(error -> write(exchange, start, error).then(Mono.error(error)));
    }

    private Mono<Void> write(ServerWebExchange exchange, long start, Throwable error) {
        var username = (String) exchange.getAttributes().get(AuditLog.USERNAME_ATTRIBUTE);
        var uidMono = username == null
                ? Mono.just("anonymous")
                : client.fetch(User.class, username)
                        .map(user -> user.getSpec().getUid())
                        .defaultIfEmpty("unknown");
        var operation = resolvedOperation(exchange);
        var type = resourceType(exchange);
        return uidMono.onErrorResume(e -> {
                    log.warn("无法解析审计记录的用户 ID", e);
                    return Mono.just("unknown");
                })
                .flatMap(uid -> ruleEvaluator.shouldRecord(operation, type).flatMap(record -> {
                    if (!Boolean.TRUE.equals(record)) {
                        return Mono.empty();
                    }
                    exchange.getAttributes().put(AuditLog.UID_ATTRIBUTE, uid);
                    try {
                        auditLog.record(
                                exchange,
                                operation,
                                type,
                                resourceName(exchange),
                                result(exchange, error),
                                System.currentTimeMillis() - start,
                                context(exchange, username, error));
                    } catch (RuntimeException e) {
                        log.error("无法为请求 {} 构建审计记录", exchange
                                .getRequest()
                                .getPath()
                                .value(), e);
                    }
                    return Mono.<Void>empty();
                }))
                .then();
    }

    private static String resolvedOperation(ServerWebExchange exchange) {
        var overridden = (String) exchange.getAttributes().get(AuditLog.OPERATION_ATTRIBUTE);
        return overridden != null ? overridden : operation(exchange);
    }

    private static String result(ServerWebExchange exchange, Throwable error) {
        var status = exchange.getResponse().getStatusCode();
        var overriddenResult = (String) exchange.getAttributes().get(AuditLog.RESULT_ATTRIBUTE);
        if (overriddenResult != null) {
            return overriddenResult;
        }
        if (error != null) {
            return "FAILURE";
        }
        if (status != null && (status.is2xxSuccessful() || status.is3xxRedirection())) {
            return "SUCCESS";
        }
        if (status == HttpStatus.UNAUTHORIZED || status == HttpStatus.FORBIDDEN) {
            return "DENIED";
        }
        return "FAILURE";
    }

    private static Map<String, Object> context(ServerWebExchange exchange, String username, Throwable error) {
        var status = exchange.getResponse().getStatusCode();
        var context = new LinkedHashMap<String, Object>();
        context.put("status", status == null ? null : status.value());
        if (error != null) {
            context.put("error", error.getClass().getName());
            context.put("message", error.getMessage());
        }
        if (username != null) {
            context.put("username", username);
        }
        var patOwner = exchange.getAttributes().get(AuditLog.PAT_OWNER_ATTRIBUTE);
        if (patOwner != null) {
            context.put("patOwner", patOwner);
        }
        var sudoUser = exchange.getAttributes().get(AuditLog.SUDO_USER_ATTRIBUTE);
        if (sudoUser != null) {
            context.put("sudoUser", sudoUser);
        }
        var extra = exchange.getAttributes().get(AuditLog.CONTEXT_ATTRIBUTE);
        if (extra != null) {
            context.put("reason", extra);
        }
        return context;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    static String operation(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().value();
        var method = exchange.getRequest().getMethod();
        if (path.contains("/login")) {
            return "LOGIN";
        }
        if (path.contains("/logout")) {
            return "LOGOUT";
        }
        if (path.contains("/passkey") || path.contains("/passkeys")) {
            return "PASSKEY";
        }
        if (path.contains("/bind") || path.contains("/userconnections")) {
            return "BIND";
        }
        if (path.contains("/auditlogs")) {
            return "AUDIT_QUERY";
        }
        if (method == null) {
            return "UNKNOWN";
        }
        var apiRequest = path.startsWith("/apis/") || path.startsWith("/api/");
        if (!apiRequest
                && ("GET".equals(method.name()) || "HEAD".equals(method.name()))
                && exchange.getRequest()
                        .getHeaders()
                        .getAccept()
                        .stream()
                        .anyMatch(mediaType -> mediaType.includes(MediaType.TEXT_HTML))) {
            return "NAVIGATION";
        }
        return methodOperation(method.name());
    }

    private static String methodOperation(String method) {
        return switch (method) {
            case "GET", "HEAD" -> "READ";
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method;
        };
    }

    static String resourceType(ServerWebExchange exchange) {
        var overridden = overriddenAttribute(exchange, AuditLog.RESOURCE_TYPE_ATTRIBUTE);
        if (overridden != null) {
            return overridden;
        }
        var parts = apiParts(exchange);
        if (parts.size() >= 4) {
            var resource = parts.get(3);
            return switch (resource) {
                case "users" -> "User";
                case "usergroups" -> "UserGroup";
                case "usergroupbindings" -> "UserGroupBinding";
                case "temprolebindings" -> "TempRoleBinding";
                case "riskrules" -> "RiskRule";
                case "webhooks" -> "Webhook";
                case "personalaccesstokens" -> "PersonalAccessToken";
                case "roles" -> "Role";
                case "rolebindings" -> "RoleBinding";
                case "auditlogs" -> "AuditRecord";
                case "userconnections" -> "UserConnection";
                case "passkeys" -> "Passkey";
                case "settings", "configmaps" -> "Setting";
                case "parameter-settings", "systemconfigs" -> "ParameterSetting";
                default -> resource;
            };
        }
        return "NAVIGATION".equals(operation(exchange)) ? "Page" : "Request";
    }

    static String resourceName(ServerWebExchange exchange) {
        var overridden = overriddenAttribute(exchange, AuditLog.RESOURCE_NAME_ATTRIBUTE);
        if (overridden != null) {
            return overridden;
        }
        var parts = apiParts(exchange);
        if (parts.size() >= 5) {
            return parts.get(4);
        }
        if (parts.size() < 4 && "NAVIGATION".equals(operation(exchange))) {
            return exchange.getRequest().getPath().pathWithinApplication().value();
        }
        return null;
    }

    private static String overriddenAttribute(ServerWebExchange exchange, String key) {
        var value = (String) exchange.getAttributes().get(key);
        return value == null || value.isBlank() ? null : value;
    }

    private static java.util.List<String> apiParts(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().pathWithinApplication().value();
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
    }
}
