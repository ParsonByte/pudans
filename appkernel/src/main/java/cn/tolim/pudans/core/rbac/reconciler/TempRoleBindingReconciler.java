package cn.tolim.pudans.core.rbac.reconciler;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.isDeleted;

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
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.controller.Controller;
import cn.tolim.pudans.extension.contract.controller.ControllerBuilder;
import cn.tolim.pudans.extension.contract.controller.ReactiveReconciler;
import cn.tolim.pudans.extension.contract.controller.Reconciler;
import cn.tolim.pudans.infra.contract.utils.JsonUtils;
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

    // 声明负责临时角色绑定，并设置最迟重排时间。
    @Override
    public Controller setupWith(ControllerBuilder builder) {
        return builder.extension(new TempRoleBinding())
                .maxDelay(Duration.ofHours(9))
                .build();
    }

    // 读取绑定并协调，对象不存在时停止重试。
    @Override
    public Mono<Result> reconcileReactive(Request request) {
        return client.fetch(TempRoleBinding.class, request.name())
                .flatMap(this::reconcileBinding)
                .switchIfEmpty(Mono.just(Result.doNotRetry()));
    }

    // 按状态撤销、到期终止或继续授予并重排。
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

    // 撤销授权、标记终态并写入审计。
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

    // 发布临时角色过期事件，供审计与通知使用。
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

    // 构造系统终态审计记录，关联原授权流程。
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

    // 解析用户内部标识，失败时使用占位值。
    private Mono<String> resolveUid(String username) {
        return client.fetch(User.class, username)
                .map(user -> user.getSpec().getUid())
                .defaultIfEmpty("unknown")
                .onErrorResume(error -> {
                    log.warn("无法解析 [{}] 的用户 ID", username, error);
                    return Mono.just("unknown");
                });
    }

    // 提取审计所需的授权关键字段。
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
