package cn.tolim.pudans.core.ai.quota;

import cn.tolim.pudans.contract.ai.AiQuotaAssignment;
import cn.tolim.pudans.contract.ai.AiQuotaPlan;
import cn.tolim.pudans.contract.ai.dto.AiQuotaAssignmentCreateRequest;
import cn.tolim.pudans.contract.ai.dto.AiQuotaAssignmentUpdateRequest;
import cn.tolim.pudans.core.ai.AiSupport;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaAssignmentRepository;
import cn.tolim.pudans.infra.r2dbc.ai.quota.AiQuotaPlanRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class AiQuotaAssignments {

    private static final Set<String> SUBJECT_TYPES = Set.of("user", "workspace", "role");

    private final AiQuotaAssignmentRepository assignments;

    private final AiQuotaPlanRepository plans;

    private Clock clock = Clock.systemUTC();

    public Flux<AiQuotaAssignment> list() {
        return assignments.findAll();
    }

    public Mono<AiQuotaAssignment> get(UUID id) {
        return assignments
                .findById(id)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND,
                        "ai-quota-assignment-not-found",
                        "配额分配不存在。")));
    }

    public Mono<AiQuotaAssignment> create(AiQuotaAssignmentCreateRequest request) {
        if (request == null) {
            return Mono.error(bodyRequired());
        }
        return Mono.defer(() -> {
            var now = Instant.now(clock);
            var effectiveFrom = request.effectiveFrom() == null ? now : request.effectiveFrom();
            var assignment = new AiQuotaAssignment(
                    UUID.randomUUID(),
                    subjectType(request.subjectType()),
                    AiSupport.requiredText(
                            request.subjectId(),
                            "ai-quota-assignment-subject-required",
                            "配额主体不能为空。"),
                    request.planId(),
                    effectiveFrom,
                    request.effectiveTo(),
                    request.weeklyAnchorAt() == null ? effectiveFrom : request.weeklyAnchorAt(),
                    AiSupport.normalizeStatus(request.status(), AiSupport.ACTIVE),
                    now,
                    now);
            return validate(assignment).then(assignments.insert(assignment));
        });
    }

    public Mono<AiQuotaAssignment> update(
            UUID id, AiQuotaAssignmentUpdateRequest request) {
        if (request == null) {
            return Mono.error(bodyRequired());
        }
        return Mono.defer(() -> get(id).flatMap(existing -> {
            var assignment = new AiQuotaAssignment(
                    existing.id(),
                    request.subjectType() == null
                            ? existing.subjectType()
                            : subjectType(request.subjectType()),
                    StringUtils.hasText(request.subjectId())
                            ? request.subjectId().trim()
                            : existing.subjectId(),
                    request.planId() == null ? existing.planId() : request.planId(),
                    request.effectiveFrom() == null
                            ? existing.effectiveFrom()
                            : request.effectiveFrom(),
                    request.effectiveTo() == null
                            ? existing.effectiveTo()
                            : request.effectiveTo(),
                    request.weeklyAnchorAt() == null
                            ? existing.weeklyAnchorAt()
                            : request.weeklyAnchorAt(),
                    request.status() == null
                            ? existing.status()
                            : AiSupport.normalizeStatus(request.status(), AiSupport.ACTIVE),
                    existing.createdAt(),
                    Instant.now(clock));
            return validate(assignment).then(assignments.update(assignment));
        }));
    }

    public Mono<Void> delete(UUID id) {
        return get(id).flatMap(assignment -> assignments.delete(assignment.id()));
    }

    public Mono<ResolvedQuota> resolve(String subjectType, String subjectId) {
        return Mono.defer(() -> {
            var type = subjectType(subjectType);
            var resolvedSubject = AiSupport.requiredText(
                    subjectId,
                    "ai-quota-assignment-subject-required",
                    "配额主体不能为空。");
            return find(type, resolvedSubject)
                    .switchIfEmpty(Mono.error(new ApiException(
                            HttpStatus.NOT_FOUND,
                            "ai-quota-assignment-not-found",
                            "当前主体没有生效的配额分配。")));
        });
    }

    public Mono<ResolvedQuota> findForIdentity(
            String ownerId, String workspaceId, Collection<String> roles) {
        return find("user", ownerId)
                .switchIfEmpty(Mono.defer(() -> find("workspace", workspaceId)))
                .switchIfEmpty(Mono.defer(() -> findRole(roles)));
    }

    public Mono<ResolvedQuota> find(String subjectType, String subjectId) {
        return findActive(subjectType, subjectId).flatMap(this::resolved);
    }

    private Mono<ResolvedQuota> findRole(Collection<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Mono.empty();
        }
        return assignments
                .findActiveRoles(roles, Instant.now(clock))
                .next()
                .flatMap(this::resolved);
    }

    private Mono<AiQuotaAssignment> findActive(String subjectType, String subjectId) {
        if (!StringUtils.hasText(subjectId)) {
            return Mono.empty();
        }
        return assignments.findActive(subjectType, subjectId.trim(), Instant.now(clock));
    }

    private Mono<ResolvedQuota> resolved(AiQuotaAssignment assignment) {
        return plans.findById(assignment.planId())
                .map(plan -> new ResolvedQuota(assignment, plan));
    }

    private Mono<Void> validate(AiQuotaAssignment assignment) {
        if (assignment.planId() == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-assignment-plan-required",
                    "配额套餐不能为空。"));
        }
        if (assignment.effectiveTo() != null
                && !assignment.effectiveTo().isAfter(assignment.effectiveFrom())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-assignment-effective-invalid",
                    "配额分配失效时间必须晚于生效时间。"));
        }
        if (assignment.weeklyAnchorAt().isBefore(assignment.effectiveFrom())) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-assignment-anchor-invalid",
                    "周窗口锚点不能早于配额分配生效时间。"));
        }
        return plans
                .findById(assignment.planId())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "ai-quota-plan-not-found", "配额套餐不存在。")))
                .then();
    }

    private static String subjectType(String value) {
        var resolved = StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT)
                : "";
        if (!SUBJECT_TYPES.contains(resolved)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "ai-quota-subject-type-invalid",
                    "配额主体类型必须为 user、workspace 或 role。");
        }
        return resolved;
    }

    private static ApiException bodyRequired() {
        return new ApiException(
                HttpStatus.BAD_REQUEST,
                "ai-quota-assignment-body-required",
                "请求体不能为空。");
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public record ResolvedQuota(AiQuotaAssignment assignment, AiQuotaPlan plan) {}
}
