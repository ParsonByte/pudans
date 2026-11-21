package cn.tolim.pudans.core.rbac.endpoint;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.api.index.query.Queries.equal;
import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;

import cn.tolim.pudans.extension.api.endpoint.CustomEndpoint;
import cn.tolim.pudans.contract.rbac.TempRoleBinding;
import cn.tolim.pudans.core.rbac.Roles;
import cn.tolim.pudans.extension.api.ApiGroups;
import cn.tolim.pudans.extension.api.GroupVersion;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.PageRequestImpl;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.TempGrants;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;
import cn.tolim.pudans.security.authorization.AuthorityUtils;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TempRoleBindingEndpoint implements CustomEndpoint {

    private final ReactiveExtensionClient client;

    private final TempGrants tempGrants;

    private final Roles roleService;

    private final AuditLog auditLog;

    private final DomainEventPublisher domainEventPublisher;

    private static Map<String, Object> snapshot(TempRoleBinding binding) {
        var spec = binding.getSpec();
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("username", spec.getUsername());
        snapshot.put("roleNames", spec.getRoleNames());
        snapshot.put("status", spec.getStatus());
        snapshot.put("expiresAt", spec.getExpiresAt() == null ? null : spec.getExpiresAt().toString());
        snapshot.put("requestedBy", spec.getRequestedBy());
        snapshot.put("approvedBy", spec.getApprovedBy());
        return snapshot;
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(ApiGroups.API_VERSION);
    }

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        var tag = "TempRoleBindingV1alpha1";
        return SpringdocRouteBuilder.route()
                .GET("/temprolebindings", this::list, builder -> builder.operationId("ListTempRoleBindings")
                        .tag(tag)
                        .description("List temporary role bindings.")
                        .response(responseBuilder().implementationArray(TempRoleBinding.class)))
                .GET("/temprolebindings/{name}", this::get, builder -> builder.operationId("GetTempRoleBinding")
                        .tag(tag)
                        .description("Get a temporary role binding.")
                        .response(responseBuilder().implementation(TempRoleBinding.class)))
                .POST("/temprolebindings", this::apply, builder -> builder.operationId("ApplyTempRoleBinding")
                        .tag(tag)
                        .description("Apply for a temporary role binding.")
                        .requestBody(requestBodyBuilder().required(true).implementation(ApplyRequest.class))
                        .response(responseBuilder().implementation(TempRoleBinding.class)))
                .POST("/temprolebindings/{name}/approve", this::approve, builder -> builder
                        .operationId("ApproveTempRoleBinding")
                        .tag(tag)
                        .description("Approve a temporary role binding and grant the roles immediately.")
                        .response(responseBuilder().implementation(TempRoleBinding.class)))
                .POST("/temprolebindings/{name}/revoke", this::revoke,
                        builder -> builder.operationId("RevokeTempRoleBinding")
                                .tag(tag)
                                .description("Revoke a temporary role binding.")
                                .response(responseBuilder().implementation(TempRoleBinding.class)))
                .build();
    }

    private Mono<ServerResponse> list(ServerRequest request) {
        var options = ListOptions.builder().andQuery(notDeleting());
        request.queryParam("username")
                .filter(StringUtils::hasText)
                .ifPresent(username -> options.andQuery(equal("spec.username", username)));
        request.queryParam("status")
                .filter(StringUtils::hasText)
                .ifPresent(status -> options.andQuery(equal("spec.status", status)));
        var page = request.queryParam("page").map(Integer::parseInt).orElse(1);
        var size = request.queryParam("size").map(Integer::parseInt).orElse(50);
        return client.listBy(TempRoleBinding.class, options.build(), PageRequestImpl.of(page, size, defaultSort()))
                .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    private Mono<ServerResponse> get(ServerRequest request) {
        return client.fetch(TempRoleBinding.class, request.pathVariable("name"))
                .switchIfEmpty(
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "临时角色绑定不存在。")))
                .flatMap(binding -> ServerResponse.ok().bodyValue(binding));
    }

    private Mono<ServerResponse> apply(ServerRequest request) {
        return currentAuthentication()
                .flatMap(authentication -> request.bodyToMono(ApplyRequest.class)
                        .switchIfEmpty(Mono.error(new ServerWebInputException("请求体为必填项。")))
                        .flatMap(body -> validateApply(body, authentication))
                        .flatMap(binding -> client
                                .create(binding)
                                .flatMap(created -> auditLog
                                        .recordChild(
                                                request.exchange(),
                                                null,
                                                "TEMP_ROLE_APPLY",
                                                TempRoleBinding.KIND,
                                                created.getMetadata().getName(),
                                                "SUCCESS",
                                                snapshot(created),
                                                null)
                                        .thenReturn(created))
                                .doOnNext(created -> publish("temprole.applied", created, request))))
                .flatMap(binding -> ServerResponse.ok().bodyValue(binding));
    }

    private Mono<ServerResponse> approve(ServerRequest request) {
        var name = request.pathVariable("name");
        return client.fetch(TempRoleBinding.class, name)
                .switchIfEmpty(
                        Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "临时角色绑定不存在。")))
                .flatMap(binding -> {
                    var spec = binding.getSpec();
                    if (TempRoleBinding.STATUS_ACTIVE.equals(spec.getStatus())) {
                        return Mono.just(binding);
                    }
                    if (!TempRoleBinding.STATUS_PENDING.equals(spec.getStatus())) {
                        return Mono.error(
                                new ServerWebInputException("只能批准待审批的临时角色绑定。"));
                    }
                    if (spec.getExpiresAt() == null || !Instant.now().isBefore(spec.getExpiresAt())) {
                        return Mono.error(new ServerWebInputException("临时角色绑定已过期。"));
                    }
                    return currentAuthentication().flatMap(authentication -> {
                        var approverRoles =
                                AuthorityUtils.authoritiesToRoles(authentication.getAuthorities());
                        return roleService
                                .contains(approverRoles, new HashSet<>(spec.getRoleNames()))
                                .flatMap(allowed -> allowed
                                        ? grantAndAudit(request, binding, authentication)
                                        : Mono.error(new ResponseStatusException(
                                                HttpStatus.FORBIDDEN, "不能授予你不拥有的角色。")));
                    });
                })
                .flatMap(binding -> ServerResponse.ok().bodyValue(binding));
    }

    private Mono<TempRoleBinding> grantAndAudit(ServerRequest request, TempRoleBinding binding,
            Authentication authentication) {
        var spec = binding.getSpec();
        spec.setStatus(TempRoleBinding.STATUS_ACTIVE);
        spec.setApprovedBy(authentication.getName());
        spec.setApprovedAt(Instant.now());
        return client.update(binding)
                .flatMap(updated -> tempGrants.grant(updated).thenReturn(updated))
                .flatMap(updated -> auditLog
                        .recordChild(
                                request.exchange(),
                                null,
                                "TEMP_ROLE_GRANT",
                                TempRoleBinding.KIND,
                                updated.getMetadata().getName(),
                                "SUCCESS",
                                snapshot(updated),
                                null)
                        .thenReturn(updated))
                .doOnNext(updated -> publish("temprole.granted", updated, request));
    }

    private void publish(String type, TempRoleBinding binding, ServerRequest request) {
        var spec = binding.getSpec();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("binding", binding.getMetadata().getName());
        payload.put("username", spec.getUsername());
        payload.put("roleNames", spec.getRoleNames());
        payload.put("status", spec.getStatus());
        payload.put("expiresAt", spec.getExpiresAt() == null ? null : spec.getExpiresAt().toString());
        domainEventPublisher.publish(
                type, payload, (String) request.exchange().getAttributes().get(AuditLog.TRACE_FLOW_ID_ATTRIBUTE));
    }

    private Mono<ServerResponse> revoke(ServerRequest request) {
        return currentAuthentication()
                .flatMap(authentication -> client.fetch(TempRoleBinding.class, request.pathVariable("name"))
                        .switchIfEmpty(Mono.error(
                                new ResponseStatusException(HttpStatus.NOT_FOUND, "临时角色绑定不存在。")))
                        .flatMap(binding -> {
                            var spec = binding.getSpec();
                            if (TempRoleBinding.STATUS_REVOKED.equals(spec.getStatus())
                                    || TempRoleBinding.STATUS_EXPIRED.equals(spec.getStatus())) {
                                return Mono.just(binding);
                            }
                            var roles = AuthorityUtils.authoritiesToRoles(authentication.getAuthorities());
                            var privileged = AuthorityUtils.containsSuperRole(roles)
                                    || authentication.getName().equals(spec.getUsername())
                                    || authentication.getName().equals(spec.getRequestedBy());
                            if (!privileged) {
                                return Mono.error(new ResponseStatusException(
                                        HttpStatus.FORBIDDEN, "你不能撤销此临时角色绑定。"));
                            }
                            spec.setStatus(TempRoleBinding.STATUS_REVOKED);
                            return client.update(binding)
                                    .flatMap(updated -> tempGrants.revoke(updated).thenReturn(updated))
                                    .flatMap(updated -> auditLog
                                            .recordChild(
                                                    request.exchange(),
                                                    null,
                                                    "TEMP_ROLE_REVOKE",
                                                    TempRoleBinding.KIND,
                                                    updated.getMetadata().getName(),
                                                    "SUCCESS",
                                                    snapshot(updated),
                                                    null)
                                            .thenReturn(updated))
                                    .doOnNext(updated -> publish("temprole.revoked", updated, request));
                        }))
                .flatMap(binding -> ServerResponse.ok().bodyValue(binding));
    }

    private Mono<TempRoleBinding> validateApply(ApplyRequest body, Authentication authentication) {
        if (CollectionUtils.isEmpty(body.roleNames())) {
            return Mono.error(new ServerWebInputException("roleNames 不能为空。"));
        }
        if (body.roleNames().contains(TempRoleBinding.SUPER_ROLE_NAME)) {
            return Mono.error(new ServerWebInputException("super-role 不能被临时授予。"));
        }
        if (body.roleNames().stream().anyMatch(role -> !StringUtils.hasText(role))) {
            return Mono.error(new ServerWebInputException("roleNames 不能包含空值。"));
        }
        var now = Instant.now();
        if (body.expiresAt() == null || !body.expiresAt().isAfter(now)) {
            return Mono.error(new ServerWebInputException("expiresAt 必须晚于当前时间。"));
        }
        if (Duration.between(now, body.expiresAt()).getSeconds() > TempRoleBinding.HARD_MAX_DURATION_SECONDS) {
            return Mono.error(new ServerWebInputException("expiresAt 与当前时间的间隔不能超过 "
                    + TempRoleBinding.HARD_MAX_DURATION_SECONDS + " 秒。"));
        }
        var username = StringUtils.hasText(body.username()) ? body.username() : authentication.getName();
        var privileged = username.equals(authentication.getName())
                || AuthorityUtils.containsSuperRole(
                AuthorityUtils.authoritiesToRoles(authentication.getAuthorities()));
        if (!privileged) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "你不能为其他用户申请。"));
        }
        var binding = TempRoleBinding.apply(
                username,
                body.roleNames(),
                body.expiresAt(),
                body.reason(),
                body.ticketId(),
                authentication.getName());
        var metadata = new Metadata();
        metadata.setName("temprole-" + username + "-" + AuditIdGenerator.next().substring(0, 8));
        binding.setMetadata(metadata);
        return Mono.just(binding);
    }

    private Mono<Authentication> currentAuthentication() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .filter(authentication -> authentication != null && authentication.isAuthenticated())
                .filter(authentication -> !(authentication instanceof AnonymousAuthenticationToken))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, "需要身份认证。")));
    }

    record ApplyRequest(String username, List<String> roleNames, Instant expiresAt, String reason, String ticketId) {}
}
