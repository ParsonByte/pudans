package cn.tolim.pudans.core.rbac.reconciler;

import static cn.tolim.pudans.extension.api.ExtensionUtil.isDeleted;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.contract.rbac.TempRoleBinding;
import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.extension.api.controller.Controller;
import cn.tolim.pudans.extension.api.controller.ControllerBuilder;
import cn.tolim.pudans.extension.api.controller.ReactiveReconciler;
import cn.tolim.pudans.extension.api.controller.Reconciler;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.event.DomainEventPublisher;
import cn.tolim.pudans.core.rbac.TempGrants;
import cn.tolim.pudans.infra.audit.AuditIdGenerator;

@Slf4j
@Component
@RequiredArgsConstructor
public class TempRoleBindingReconciler implements ReactiveReconciler<Reconciler.Request> {

    private final ReactiveExtensionClient client;

    private final TempGrants tempGrants;

    private final AuditLog auditLog;

    private final DomainEventPublisher domainEventPublisher;

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new TempRoleBinding())
                .maxDelay(Duration.ofHours(9))
                .build();
    }

    @Override
    public Mono<Result> reconcileReactive(Request request) {
        return client.fetch(TempRoleBinding.class, request.name())
                .flatMap(this::reconcileBinding)
                .switchIfEmpty(Mono.just(Result.doNotRetry()));
    }

    private Mono<Result> reconcileBinding(TempRoleBinding binding) {
        var spec = binding.getSpec();
        if (isDeleted(binding) || TempRoleBinding.STATUS_REVOKED.equals(spec.getStatus())) {
            return tempGrants.revoke(binding).thenReturn(Result.doNotRetry());
        }
        var now = Instant.now();
        if (TempRoleBinding.STATUS_ACTIVE.equals(spec.getStatus())) {
            if (!binding.isActiveAt(now)) {
                return terminate(binding, TempRoleBinding.STATUS_EXPIRED, "TEMP_ROLE_EXPIRE");
            }
            return tempGrants
                    .grant(binding)
                    .thenReturn(Result.requeue(
                            Duration.between(now, spec.getExpiresAt()).plusSeconds(1)));
        }
        if (TempRoleBinding.STATUS_PENDING.equals(spec.getStatus())) {
            if (spec.getExpiresAt() == null || !now.isBefore(spec.getExpiresAt())) {
                return terminate(binding, TempRoleBinding.STATUS_EXPIRED, "TEMP_ROLE_EXPIRE");
            }
            return Mono.just(Result.requeue(Duration.between(now, spec.getExpiresAt()).plusSeconds(1)));
        }
        return Mono.just(Result.doNotRetry());
    }

    private Mono<Result> terminate(TempRoleBinding binding, String status, String operation) {
        return tempGrants
                .revoke(binding)
                .then(resolveUid(binding.getSpec().getUsername()))
                .flatMap(uid -> {
                    var spec = binding.getSpec();
                    spec.setStatus(status);
                    spec.setRevokeAuditId(recordTerminalAudit(binding, operation, uid));
                    return client.update(binding);
                })
                .doOnNext(this::publishExpired)
                .thenReturn(Result.doNotRetry());
    }

    private void publishExpired(TempRoleBinding binding) {
        var spec = binding.getSpec();
        var payload = new LinkedHashMap<String, Object>();
        payload.put("binding", binding.getMetadata().getName());
        payload.put("username", spec.getUsername());
        payload.put("roleNames", spec.getRoleNames());
        payload.put("status", spec.getStatus());
        payload.put("expiredAt", Instant.now().toString());
        domainEventPublisher.publish("temprole.expired", payload, null);
    }

    private String recordTerminalAudit(TempRoleBinding binding, String operation, String uid) {
        var record = auditLog.newRecord(AuditIdGenerator.next(), uid, operation);
        record.setTraceFlowId(AuditIdGenerator.next());
        record.markSyntheticFlow();
        record.setStepIndex(1);
        record.setResourceType(TempRoleBinding.KIND);
        record.setResourceName(binding.getMetadata().getName());
        record.setResult("SUCCESS");
        record.setTimestamp(Instant.now());
        record.setDurationMs(0L);
        record.setContext(snapshot(binding));
        record.setActionSnapshot(JsonUtils.objectToJson(snapshot(binding)));
        auditLog.record(record);
        return record.getMetadata().getName();
    }

    private Mono<String> resolveUid(String username) {
        return client.fetch(User.class, username)
                .map(user -> user.getSpec().getUid())
                .defaultIfEmpty("unknown")
                .onErrorResume(error -> {
                    log.warn("无法解析 [{}] 的用户 ID", username, error);
                    return Mono.just("unknown");
                });
    }

    private static Map<String, Object> snapshot(TempRoleBinding binding) {
        var spec = binding.getSpec();
        var snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("username", spec.getUsername());
        snapshot.put("roleNames", spec.getRoleNames());
        snapshot.put("status", spec.getStatus());
        snapshot.put("expiresAt", spec.getExpiresAt() == null ? null : spec.getExpiresAt().toString());
        snapshot.put("approvedBy", spec.getApprovedBy());
        return snapshot;
    }
}
