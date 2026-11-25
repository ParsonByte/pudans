package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryPoolItem;
import cn.tolim.pudans.contract.agent.dto.MemoryPoolReviewRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryPoolReviews {

    private static final Set<String> STATUSES = Set.of(
            "candidate", "approved", "published", "rejected", "superseded");

    private static final int MAX_LIMIT = 100;

    private final MemoryPoolRepository pool;

    private final MemoryPoolSourceSupport support;

    private final MemoryAccess access;

    public Flux<MemoryPoolItem> list(
            RequestIdentity identity,
            Collection<String> roles,
            String requestedStatus,
            int requestedLimit) {
        var status = status(requestedStatus == null ? "published" : requestedStatus);
        var limit = requestedLimit <= 0 ? 20 : Math.min(requestedLimit, MAX_LIMIT);
        var visible = "published".equals(status)
                ? Mono.empty()
                : access.require(identity, roles, "review");
        return visible.thenMany(
                pool.listByStatus(status, limit).map(MemoryPoolEntity::toContract));
    }

    public Mono<MemoryPoolItem> approve(
            RequestIdentity identity,
            Collection<String> roles,
            UUID id,
            MemoryPoolReviewRequest request) {
        return access
                .require(identity, roles, "review")
                .then(require(id))
                .flatMap(entity -> support.refresh(entity.getId()))
                .flatMap(entity -> {
                    if (!"candidate".equals(entity.getStatus())) {
                        return Mono.error(conflict("只有候选记忆可以审核通过。"));
                    }
                    if (MemoryPoolSourceSupport.insufficient(
                            entity.getSourceOwnerCount(), entity.getSourceWorkspaceCount())) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "memory-pool-sources-insufficient",
                                "记忆池候选必须来自至少两个不同 owner 和两个来源工作区。"));
                    }
                    if (!"general".equals(entity.getSafetyClass())) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "memory-pool-safety-blocked",
                                "敏感记忆不能进入审核通过状态。"));
                    }
                    return reviewedText(entity, request).flatMap(text -> {
                        entity.setGeneralizedContent(text);
                        entity.setContentHash(MemoryText.contentHash(text));
                        entity.setSimhash(MemoryText.simhash(text));
                        entity.setStatus("approved");
                        entity.setReviewerId(identity.ownerId());
                        entity.setReviewedAt(Instant.now());
                        entity.setUpdatedAt(Instant.now());
                        return pool.update(entity);
                    });
                })
                .map(MemoryPoolEntity::toContract);
    }

    public Mono<MemoryPoolItem> publish(
            RequestIdentity identity, Collection<String> roles, UUID id) {
        return access
                .require(identity, roles, "publish")
                .then(require(id))
                .flatMap(entity -> support.refresh(entity.getId()))
                .flatMap(entity -> {
                    if (!"approved".equals(entity.getStatus())) {
                        return Mono.error(conflict("只有已审核的记忆可以发布。"));
                    }
                    if (MemoryPoolSourceSupport.insufficient(
                            entity.getSourceOwnerCount(), entity.getSourceWorkspaceCount())) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "memory-pool-sources-insufficient",
                                "记忆池来源数量不足，不能发布。"));
                    }
                    var text = entity.getGeneralizedContent();
                    if (!"general".equals(entity.getSafetyClass())
                            || !StringUtils.hasText(text)
                            || MemoryText.containsPii(text)) {
                        return Mono.error(new ApiException(
                                HttpStatus.CONFLICT,
                                "memory-pool-safety-blocked",
                                "记忆内容未通过发布安全检查。"));
                    }
                    var now = Instant.now();
                    entity.setStatus("published");
                    entity.setReviewerId(identity.ownerId());
                    entity.setReviewedAt(now);
                    entity.setNextReviewAt(now.plus(90, ChronoUnit.DAYS));
                    entity.setCanonicalId(entity.getCanonicalId() == null
                            ? entity.getId()
                            : entity.getCanonicalId());
                    entity.setUpdatedAt(now);
                    return pool.update(entity);
                })
                .map(MemoryPoolEntity::toContract);
    }

    public Mono<MemoryPoolItem> reject(
            RequestIdentity identity,
            Collection<String> roles,
            UUID id,
            MemoryPoolReviewRequest request) {
        return access
                .require(identity, roles, "review")
                .then(require(id))
                .flatMap(entity -> {
                    if (!Set.of("candidate", "approved").contains(entity.getStatus())) {
                        return Mono.error(conflict("当前记忆池状态不能拒绝。"));
                    }
                    var evidence = new LinkedHashMap<>(entity.getEvidenceSummary());
                    if (request != null && StringUtils.hasText(request.reason())) {
                        evidence.put("reviewReason", request.reason().trim());
                    }
                    var now = Instant.now();
                    entity.setEvidenceSummary(evidence);
                    entity.setStatus("rejected");
                    entity.setReviewerId(identity.ownerId());
                    entity.setReviewedAt(now);
                    entity.setUpdatedAt(now);
                    return pool.update(entity);
                })
                .map(MemoryPoolEntity::toContract);
    }

    public Mono<MemoryPoolItem> withdraw(
            RequestIdentity identity, Collection<String> roles, UUID id) {
        return access
                .require(identity, roles, "admin")
                .then(require(id))
                .flatMap(entity -> {
                    if (!Set.of("approved", "published").contains(entity.getStatus())) {
                        return Mono.error(conflict("当前记忆池状态不能撤回。"));
                    }
                    var now = Instant.now();
                    entity.setStatus("superseded");
                    entity.setValidTo(now);
                    entity.setReviewerId(identity.ownerId());
                    entity.setReviewedAt(now);
                    entity.setUpdatedAt(now);
                    return pool.update(entity);
                })
                .map(MemoryPoolEntity::toContract);
    }

    private Mono<String> reviewedText(
            MemoryPoolEntity entity, MemoryPoolReviewRequest request) {
        var text = request != null && StringUtils.hasText(request.generalizedContent())
                ? request.generalizedContent().trim()
                : entity.getGeneralizedContent();
        if (!StringUtils.hasText(text) || MemoryText.containsPii(text)) {
            return Mono.error(new ApiException(
                    HttpStatus.CONFLICT,
                    "memory-pool-safety-blocked",
                    "记忆池文本未通过去个人化检查。"));
        }
        if (request != null && request.summary() != null) {
            var summary = request.summary().trim();
            if (MemoryText.containsPii(summary)) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT,
                        "memory-pool-safety-blocked",
                        "记忆池摘要未通过去个人化检查。"));
            }
            entity.setSummary(summary);
        }
        return Mono.just(text);
    }

    private Mono<MemoryPoolEntity> require(UUID id) {
        return pool.findById(id).switchIfEmpty(Mono.error(new ApiException(
                HttpStatus.NOT_FOUND, "memory-pool-item-not-found", "记忆池条目不存在。")));
    }

    private static String status(String value) {
        var status = value.trim().toLowerCase();
        if (!STATUSES.contains(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-pool-status-invalid", "记忆池状态不合法。");
        }
        return status;
    }

    private static ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, "memory-pool-status-invalid", message);
    }
}
