package cn.tolim.pudans.security.audit;

import java.time.Instant;
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
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.audit.TraceContext;
import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.security.AdditionalWebFilter;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.infra.PudansAttributes;

@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class AuditWebFilter implements AdditionalWebFilter {

    private static final String AUDITED_ATTRIBUTE = AuditWebFilter.class.getName() + ".AUDITED";

    private static final int MAX_ID_LENGTH = 64;

    private static final String[] STATIC_PREFIXES = {
        "/assets/", "/images/", "/js/", "/styles/", "/webjars/", "/favicon.ico"
    };

    private final AuditService auditService;

    private final ReactiveExtensionClient client;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (isStatic(exchange)) {
            return chain.filter(exchange);
        }
        
        
        if (exchange.getAttributes().putIfAbsent(AUDITED_ATTRIBUTE, Boolean.TRUE) != null) {
            return chain.filter(exchange);
        }
        var start = System.currentTimeMillis();
        var traceId = AuditService.traceId(exchange);
        var inheritedFlowId = truncate(
                exchange.getRequest().getHeaders().getFirst(AuditService.TRACE_FLOW_ID_HEADER), MAX_ID_LENGTH);
        var existingFlowId = (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE);
        var traceFlowId = existingFlowId != null && !existingFlowId.isBlank()
                ? existingFlowId
                : inheritedFlowId == null || inheritedFlowId.isBlank()
                        ? cn.tolim.pudans.infra.audit.AuditIdGenerator.next()
                        : inheritedFlowId;
        var rootAuditId = cn.tolim.pudans.infra.audit.AuditIdGenerator.next();
        var trail = new cn.tolim.pudans.core.audit.service.AuditTrail(traceFlowId, rootAuditId);
        exchange.getAttributes().putIfAbsent(AuditService.TRACE_FLOW_ID_ATTRIBUTE, traceFlowId);
        exchange.getAttributes().put(AuditService.AUDIT_ID_ATTRIBUTE, rootAuditId);
        exchange.getAttributes().put(AuditService.STEP_INDEX_ATTRIBUTE, 1);
        exchange.getAttributes().put(AuditService.AUDIT_TRAIL_ATTRIBUTE, trail);
        var inheritedParent = truncate(
                exchange.getRequest().getHeaders().getFirst(AuditService.PARENT_AUDIT_ID_HEADER), MAX_ID_LENGTH);
        if (inheritedParent != null && !inheritedParent.isBlank()) {
            exchange.getAttributes().put(AuditService.PARENT_AUDIT_ID_ATTRIBUTE, inheritedParent);
        }
        exchange.getResponse().getHeaders().set(AuditService.TRACE_ID_HEADER, traceId);
        exchange.getResponse().getHeaders().set(AuditService.TRACE_FLOW_ID_HEADER, traceFlowId);
        return chain.filter(exchange)
                .contextWrite(context -> context.put(
                                cn.tolim.pudans.core.audit.service.AuditTrail.class, trail)
                        .put(TraceContext.TRACE_FLOW_ID_CONTEXT_KEY, traceFlowId))
                .then(Mono.defer(() -> write(exchange, start, null)))
                .onErrorResume(error -> write(exchange, start, error).then(Mono.error(error)));
    }

    private Mono<Void> write(ServerWebExchange exchange, long start, Throwable error) {
        var username = (String) exchange.getAttributes().get(AuditService.USERNAME_ATTRIBUTE);
        var uidMono = username == null
                ? Mono.just("anonymous")
                : client.fetch(User.class, username)
                        .map(user -> user.getSpec().getUid())
                        .defaultIfEmpty("unknown");
        return uidMono.onErrorResume(e -> {
                    log.warn("Unable to resolve the uid of the audit record", e);
                    return Mono.just("unknown");
                })
                .doOnNext(uid -> {
                    exchange.getAttributes().put(AuditService.UID_ATTRIBUTE, uid);
                    try {
                        auditService.record(
                                exchange,
                                resolvedOperation(exchange),
                                resourceType(exchange),
                                resourceName(exchange),
                                result(exchange, error),
                                System.currentTimeMillis() - start,
                                context(exchange, username, error));
                    } catch (RuntimeException e) {
                        log.error("Unable to build the audit record for request {}", exchange
                                .getRequest()
                                .getPath()
                                .value(), e);
                    }
                })
                .then();
    }

    private static String resolvedOperation(ServerWebExchange exchange) {
        var overridden = (String) exchange.getAttributes().get(AuditService.OPERATION_ATTRIBUTE);
        return overridden != null ? overridden : operation(exchange);
    }

    private static String result(ServerWebExchange exchange, Throwable error) {
        var status = exchange.getResponse().getStatusCode();
        var overriddenResult = (String) exchange.getAttributes().get(AuditService.RESULT_ATTRIBUTE);
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
        var extra = exchange.getAttributes().get(AuditService.CONTEXT_ATTRIBUTE);
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

    private static boolean isStatic(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().value();
        for (var prefix : STATIC_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
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
        return switch (method.name()) {
            case "GET", "HEAD" -> exchange.getRequest()
                            .getHeaders()
                            .getAccept()
                            .stream()
                            .anyMatch(mediaType -> mediaType.includes(MediaType.TEXT_HTML))
                    ? "NAVIGATION"
                    : "READ";
            case "POST" -> "CREATE";
            case "PUT", "PATCH" -> "UPDATE";
            case "DELETE" -> "DELETE";
            default -> method.name();
        };
    }

    static String resourceType(ServerWebExchange exchange) {
        var pluginId = (String) exchange.getAttributes().get(PudansAttributes.PLUGIN_ID_ATTRIBUTE);
        if (pluginId != null && !pluginId.isBlank()) {
            return "plugin:" + pluginId;
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
                default -> resource;
            };
        }
        return "Request";
    }

    static String resourceName(ServerWebExchange exchange) {
        var parts = apiParts(exchange);
        return parts.size() >= 5 ? parts.get(4) : null;
    }

    private static java.util.List<String> apiParts(ServerWebExchange exchange) {
        var path = exchange.getRequest().getPath().pathWithinApplication().value();
        return java.util.Arrays.stream(path.split("/"))
                .filter(segment -> !segment.isBlank())
                .toList();
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
