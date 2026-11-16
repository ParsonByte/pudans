package cn.tolim.pudans.security.authorization;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.core.user.service.RoleService;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.rbac.service.TempRoleService;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.infra.utils.IpAddressUtils;

@Data
@Slf4j
public class DefaultRuleResolver implements AuthorizationRuleResolver {

    private static final Set<String> READ_VERBS = Set.of("get", "list", "watch");

    private static final Set<String> WRITE_VERBS =
            Set.of("create", "update", "patch", "delete", "deletecollection");

    private static final String PAT_NAME_CLAIM = "pat_name";

    private RoleService roleService;

    private TempRoleService tempRoleService;

    private AuditService auditService;

    public DefaultRuleResolver(
            RoleService roleService,
            TempRoleService tempRoleService,
            AuditService auditService) {
        this.roleService = roleService;
        this.tempRoleService = tempRoleService;
        this.auditService = auditService;
    }

    @Override
    public Mono<AuthorizingVisitor> visitRules(Authentication authentication, RequestInfo requestInfo) {
        return visitRules(authentication, requestInfo, null);
    }

    @Override
    public Mono<AuthorizingVisitor> visitRules(
            Authentication authentication, RequestInfo requestInfo, ServerWebExchange exchange) {
        var record = new AttributesRecord(requestInfo);
        var visitor = new AuthorizingVisitor(record);

        if (StringUtils.isNotBlank(requestInfo.getUserspace())) {
            if (!authentication.getName().equals(requestInfo.getUserspace())) {
                return Mono.fromSupplier(() -> {
                    visitor.visit(null, null, null);
                    return visitor;
                });
            }
        }

        var scopes = AuthorityUtils.authoritiesToScopes(authentication.getAuthorities());
        if (!scopes.isEmpty() && !scopeAllows(scopes, requestInfo)) {
            visitor.visit(
                    null,
                    null,
                    new PatScopeDeniedException(
                            resourceOf(requestInfo), requestInfo.getVerb(), scopes));
            auditPatDenied(authentication, requestInfo, exchange, scopes);
            return Mono.just(visitor);
        }

        var stopVisiting = new AtomicBoolean(false);
        return resolveRoleNames(authentication)
                .flatMap(roleNames -> visitRoleRules(authentication, roleNames, visitor, stopVisiting))
                .thenReturn(visitor)
                .onErrorResume(t -> {
                    log.error("访问规则时发生错误", t);
                    visitor.visit(null, null, t);
                    return Mono.just(visitor);
                });
    }

    private Mono<Void> visitRoleRules(
            Authentication authentication,
            Set<String> roleNames,
            AuthorizingVisitor visitor,
            AtomicBoolean stopVisiting) {
        return roleService
                .listDependenciesFlux(roleNames)
                .filter(role -> !CollectionUtils.isEmpty(role.getRules()))
                .doOnNext(role -> {
                    if (stopVisiting.get()) {
                        return;
                    }
                    String roleName = role.getMetadata().getName();
                    var rules = role.getRules();
                    var source = roleBindingDescriber(roleName, authentication.getName());
                    for (var rule : rules) {
                        if (!visitor.visit(source, rule, null)) {
                            stopVisiting.set(true);
                            return;
                        }
                    }
                })
                .takeUntil(item -> stopVisiting.get())
                .then();
    }

    private Mono<Set<String>> resolveRoleNames(Authentication authentication) {
        var baseRoles = AuthorityUtils.authoritiesToRoles(authentication.getAuthorities());
        if (!authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            return Mono.just(baseRoles);
        }
        return tempRoleService
                .activeRoleNameSet(authentication.getName())
                .map(tempRoles -> {
                    Set<String> merged = new HashSet<>(baseRoles);
                    merged.addAll(tempRoles);
                    return merged;
                })
                .onErrorResume(error -> {
                    log.warn("无法解析 [{}] 的临时角色", authentication.getName(), error);
                    return Mono.just(baseRoles);
                })
                .switchIfEmpty(Mono.just(baseRoles));
    }

    static boolean scopeAllows(Set<String> scopes, RequestInfo requestInfo) {
        if (scopes.contains("*") || scopes.contains("*:*")) {
            return true;
        }
        var path = requestInfo.getPath();
        var resource = requestInfo.getResource();
        if (StringUtils.isBlank(resource)) {
            return scopes.stream().anyMatch(scope -> matchesPathScope(scope, path));
        }
        var candidates = new ArrayList<String>();
        candidates.add(resource);
        if (StringUtils.isNotBlank(requestInfo.getSubresource())) {
            candidates.add(resource + '/' + requestInfo.getSubresource());
        }
        for (var scope : scopes) {
            var separator = scope.indexOf(':');
            if (separator <= 0 || separator == scope.length() - 1) {
                continue;
            }
            var scopeResource = scope.substring(0, separator);
            var scopeVerb = scope.substring(separator + 1);
            if (!verbMatches(scopeVerb, requestInfo.getVerb())) {
                continue;
            }
            for (var candidate : candidates) {
                if ("*".equals(scopeResource) || matchesResource(scopeResource, candidate)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesPathScope(String scope, String path) {
        if (scope.equals(path)) {
            return true;
        }
        var separator = scope.lastIndexOf(':');
        if (separator > 0) {
            var scopePath = scope.substring(0, separator);
            if (scopePath.equals(path)) {
                return true;
            }
            if (scopePath.endsWith("/*")
                    && path.startsWith(scopePath.substring(0, scopePath.length() - 1))) {
                return true;
            }
        }
        return scope.endsWith("/*") && path.startsWith(scope.substring(0, scope.length() - 1));
    }

    private static boolean verbMatches(String scopeVerb, String requestVerb) {
        if ("*".equals(scopeVerb) || scopeVerb.equals(requestVerb)) {
            return true;
        }
        if ("read".equals(scopeVerb)) {
            return READ_VERBS.contains(requestVerb);
        }
        if ("write".equals(scopeVerb)) {
            return WRITE_VERBS.contains(requestVerb);
        }
        return false;
    }

    private static boolean matchesResource(String scopeResource, String candidate) {
        return scopeResource.equals(candidate);
    }

    private static String resourceOf(RequestInfo requestInfo) {
        var resource = requestInfo.getResource();
        if (StringUtils.isBlank(requestInfo.getSubresource())) {
            return resource;
        }
        return resource + '/' + requestInfo.getSubresource();
    }

    private void auditPatDenied(
            Authentication authentication, RequestInfo requestInfo, ServerWebExchange exchange, Set<String> scopes) {
        if (exchange == null || auditService == null) {
            return;
        }
        var traceId = AuditService.traceId(exchange);
        var traceFlowId = (String) exchange.getAttributes().get(AuditService.TRACE_FLOW_ID_ATTRIBUTE);
        var syntheticFlow = traceFlowId == null || traceFlowId.isBlank();
        if (syntheticFlow) {
            traceFlowId = AuditIdGenerator.next();
            exchange.getAttributes().put(AuditService.TRACE_FLOW_ID_ATTRIBUTE, traceFlowId);
        }
        var uid = (String) exchange.getAttributes().get(AuditService.UID_ATTRIBUTE);
        if (uid == null || uid.isBlank()) {
            uid = authentication.getName();
        }
        var auditRecord = auditService.newRecord(traceId, uid, "PAT_DENIED");
        auditRecord.setTraceFlowId(traceFlowId);
        if (syntheticFlow) {
            auditRecord.markSyntheticFlow();
        }
        auditRecord.setParentAuditId((String) exchange.getAttributes().get(AuditService.AUDIT_ID_ATTRIBUTE));
        auditRecord.setStepIndex(1);
        auditRecord.setResourceType("PersonalAccessToken");
        auditRecord.setResourceName(resourceOf(requestInfo));
        auditRecord.setRequestMethod(exchange.getRequest().getMethod().name());
        auditRecord.setRequestPath(requestInfo.getPath());
        auditRecord.setIp(IpAddressUtils.getClientIp(exchange.getRequest()));
        auditRecord.setUserAgent(exchange.getRequest().getHeaders().getFirst("User-Agent"));
        auditRecord.setResult("DENIED");
        auditRecord.setDurationMs(0L);
        auditRecord.setTimestamp(java.time.Instant.now());
        var context = new LinkedHashMap<String, Object>();
        context.put("resource", resourceOf(requestInfo));
        context.put("verb", requestInfo.getVerb());
        context.put("scopes", scopes);
        context.put("patName", patName(authentication));
        auditRecord.setContext(context);
        auditRecord.setActionSnapshot(JsonUtils.objectToJson(context));
        auditService.record(auditRecord);
    }

    private static String patName(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwt) {
            return jwt.getToken().getClaimAsString(PAT_NAME_CLAIM);
        }
        return null;
    }

    String roleBindingDescriber(String roleName, String subject) {
        return String.format("Binding role [%s] to [%s]", roleName, subject);
    }
}
