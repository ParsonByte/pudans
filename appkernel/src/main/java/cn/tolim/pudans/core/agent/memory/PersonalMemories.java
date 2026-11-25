package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.contract.agent.MemoryItem;
import cn.tolim.pudans.contract.agent.dto.MemoryCorrectionRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryCreateRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryUpdateRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryWithdrawRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryDedupDecisionRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
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
public class PersonalMemories {

    private static final int MAX_LIMIT = 100;

    private static final int MAX_CONTENT_LENGTH = 20_000;

    private static final BigDecimal MIN_CONFIDENCE = BigDecimal.ZERO;

    private static final BigDecimal MAX_CONFIDENCE = BigDecimal.ONE;

    private static final Set<String> STATUSES = Set.of(
            "active", "superseded", "rejected", "expired", "needs_review");

    private final MemoryPersonalRepository personal;

    private final MemoryDedupDecisionRepository decisions;

    private final MemoryPoolSourceSupport poolSupport;

    private final MemoryEvents events;

    private Clock clock = Clock.systemUTC();

    public Flux<MemoryItem> list(
            RequestIdentity identity, String layer, String status, int requestedLimit) {
        var normalizedLayer = normalizeLayer(layer, true);
        var normalizedStatus = normalizeStatus(status);
        return personal
                .listOwned(
                        identity.ownerId(),
                        identity.workspaceId(),
                        normalizedLayer,
                        normalizedStatus,
                        limit(requestedLimit))
                .map(MemoryPersonalEntity::toContract);
    }

    public Mono<MemoryItem> view(RequestIdentity identity, UUID id) {
        return require(identity, id).map(MemoryPersonalEntity::toContract);
    }

    public Mono<MemoryItem> create(RequestIdentity identity, MemoryCreateRequest request) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-body-required", "记忆内容不能为空。"));
        }
        return store(identity, new MemoryDraft(
                request.layer(),
                request.memoryType(),
                request.canonicalKey(),
                request.content(),
                request.summary(),
                request.evidence() == null ? List.of() : request.evidence(),
                request.sourceIds() == null ? List.of() : request.sourceIds(),
                request.confidence(),
                request.validFrom(),
                request.validTo()))
                .map(MemoryPersonalEntity::toContract);
    }

    public Mono<MemoryItem> extract(RequestIdentity identity, ExtractedMemory extracted) {
        if (extracted == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-extraction-empty",
                    "抽取结果不能为空。"));
        }
        return store(identity, new MemoryDraft(
                extracted.layer(),
                extracted.memoryType(),
                extracted.canonicalKey(),
                extracted.content(),
                extracted.summary(),
                extracted.evidence(),
                extracted.sourceIds(),
                extracted.confidence(),
                null,
                null))
                .map(MemoryPersonalEntity::toContract);
    }

    public Mono<MemoryItem> update(
            RequestIdentity identity, UUID id, MemoryUpdateRequest request) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-body-required", "记忆更新内容不能为空。"));
        }
        return require(identity, id).flatMap(entity -> {
            if (!"active".equals(entity.getStatus())) {
                return Mono.error(notActive());
            }
            if (request.memoryType() != null) {
                entity.setMemoryType(memoryType(request.memoryType()));
            }
            if (request.summary() != null) {
                entity.setSummary(request.summary().trim());
            }
            if (request.confidence() != null) {
                entity.setConfidence(confidence(request.confidence()));
            }
            if (request.validTo() != null) {
                validateValidity(entity.getValidFrom(), request.validTo());
                entity.setValidTo(request.validTo());
            }
            entity.setUpdatedAt(now());
            return personal.update(entity).flatMap(saved -> publishUpdated(identity, saved));
        }).map(MemoryPersonalEntity::toContract);
    }

    public Mono<MemoryItem> correct(
            RequestIdentity identity, UUID id, MemoryCorrectionRequest request) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-body-required", "修正内容不能为空。"));
        }
        return require(identity, id).flatMap(current -> {
            if (!"active".equals(current.getStatus())) {
                return Mono.error(notActive());
            }
            var now = now();
            var content = text(
                    request.content() == null ? current.getContent() : request.content(),
                    "memory-content-required",
                    "记忆内容不能为空。");
            validateContent(content);
            var replacement = baseEntity(identity);
            replacement.setLayer(current.getLayer());
            replacement.setMemoryType(request.memoryType() == null
                    ? current.getMemoryType()
                    : memoryType(request.memoryType()));
            replacement.setCanonicalKey(MemoryText.canonicalKey(
                    request.canonicalKey() == null
                            ? current.getCanonicalKey()
                            : request.canonicalKey()));
            replacement.setContent(content);
            replacement.setSummary(request.summary() == null
                    ? current.getSummary()
                    : request.summary().trim());
            replacement.setEvidence(mergedEvidence(
                    current.getEvidence(),
                    request.evidence() == null ? List.of() : request.evidence(),
                    correctionEvidence(current.getId(), request.reason())));
            replacement.setSourceIds(mergedStrings(current.getSourceIds(), request.sourceIds()));
            replacement.setConfidence(confidence(request.confidence() == null
                    ? current.getConfidence()
                    : request.confidence()));
            replacement.setStatus("active");
            replacement.setValidFrom(request.validFrom() == null ? now : request.validFrom());
            replacement.setValidTo(request.validTo());
            validateValidity(replacement.getValidFrom(), replacement.getValidTo());
            replacement.setContentHash(MemoryText.contentHash(content));
            replacement.setSimhash(MemoryText.simhash(content));
            replacement.setCanonicalId(current.getCanonicalId() == null
                    ? current.getId()
                    : current.getCanonicalId());
            replacement.setCreatedAt(now);
            replacement.setUpdatedAt(now);

            current.setStatus("superseded");
            current.setValidTo(now);
            current.setSupersededBy(replacement.getId());
            current.setUpdatedAt(now);
            return personal
                    .update(current)
                    .then(personal.insert(replacement))
                    .then(decisions.insert(
                            UUID.randomUUID(),
                            replacement.getId(),
                            current.getId(),
                            "refines",
                            null,
                            null,
                            null,
                            null,
                            Map.of("source", "manual_correction"),
                            null,
                            now))
                    .then(publishUpdated(identity, replacement));
        }).map(MemoryPersonalEntity::toContract);
    }

    public Mono<MemoryItem> withdraw(
            RequestIdentity identity, UUID id, MemoryWithdrawRequest request) {
        return require(identity, id).flatMap(entity -> {
            if ("rejected".equals(entity.getStatus())) {
                return Mono.just(entity);
            }
            if (!"active".equals(entity.getStatus())) {
                return Mono.error(notActive());
            }
            var now = now();
            entity.setStatus("rejected");
            entity.setValidTo(now);
            entity.setUpdatedAt(now);
            if (request != null && StringUtils.hasText(request.reason())) {
                entity.setEvidence(mergedEvidence(
                        entity.getEvidence(),
                        List.of(),
                        Map.of("type", "withdrawal", "reason", request.reason().trim())));
            }
            return personal.update(entity).flatMap(saved -> poolSupport
                    .afterSourcesChanged(List.of(saved.getId().toString()))
                    .then(publishUpdated(identity, saved)));
        }).map(MemoryPersonalEntity::toContract);
    }

    private Mono<MemoryPersonalEntity> store(RequestIdentity identity, MemoryDraft draft) {
        return Mono.defer(() -> {
            var layer = normalizeLayer(draft.layer(), false);
            var memoryType = memoryType(draft.memoryType());
            var content = text(
                    draft.content(), "memory-content-required", "记忆内容不能为空。");
            validateContent(content);
            var confidence = confidence(draft.confidence());
            validateValidity(draft.validFrom(), draft.validTo());
            var contentHash = MemoryText.contentHash(content);
            var now = now();
            return personal
                    .findActiveByHash(
                            identity.ownerId(), identity.workspaceId(), layer, contentHash)
                    .flatMap(existing -> mergeDuplicate(existing, draft, now))
                    .switchIfEmpty(Mono.defer(() -> {
                        var entity = baseEntity(identity);
                        entity.setLayer(layer);
                        entity.setMemoryType(memoryType);
                        entity.setCanonicalKey(MemoryText.canonicalKey(draft.canonicalKey()));
                        entity.setContent(content);
                        entity.setSummary(draft.summary() == null ? null : draft.summary().trim());
                        entity.setEvidence(copyEvidence(draft.evidence()));
                        entity.setSourceIds(mergedStrings(List.of(), draft.sourceIds()));
                        entity.setConfidence(confidence);
                        entity.setStatus("active");
                        entity.setValidFrom(draft.validFrom() == null ? now : draft.validFrom());
                        entity.setValidTo(draft.validTo());
                        validateValidity(entity.getValidFrom(), entity.getValidTo());
                        entity.setContentHash(contentHash);
                        entity.setSimhash(MemoryText.simhash(content));
                        entity.setCanonicalId(entity.getId());
                        entity.setCreatedAt(now);
                        entity.setUpdatedAt(now);
                        return withCanonicalVersion(identity, entity, now);
                    }))
                    .flatMap(saved -> publishUpdated(identity, saved));
        });
    }

    private Mono<MemoryPersonalEntity> withCanonicalVersion(
            RequestIdentity identity,
            MemoryPersonalEntity entity,
            Instant now) {
        if (!StringUtils.hasText(entity.getCanonicalKey())) {
            return personal.insert(entity);
        }
        return personal
                .findActiveByCanonicalKey(
                        identity.ownerId(), identity.workspaceId(), entity.getCanonicalKey())
                .flatMap(current -> {
                    entity.setCanonicalId(current.getCanonicalId() == null
                            ? current.getId()
                            : current.getCanonicalId());
                    current.setStatus("superseded");
                    current.setValidTo(now);
                    current.setSupersededBy(entity.getId());
                    current.setUpdatedAt(now);
                    return personal
                            .update(current)
                            .then(personal.insert(entity))
                            .then(decisions.insert(
                                    UUID.randomUUID(),
                                    entity.getId(),
                                    current.getId(),
                                    "refines",
                                    null,
                                    1.0,
                                    null,
                                    null,
                                    Map.of("source", "canonical_key"),
                                    null,
                                    now))
                            .thenReturn(entity);
                })
                .switchIfEmpty(personal.insert(entity));
    }

    private Mono<MemoryPersonalEntity> mergeDuplicate(
            MemoryPersonalEntity existing, MemoryDraft draft, Instant now) {
        existing.setEvidence(mergedEvidence(
                existing.getEvidence(), draft.evidence(), Map.of()));
        existing.setSourceIds(mergedStrings(existing.getSourceIds(), draft.sourceIds()));
        if (draft.confidence() != null
                && confidence(draft.confidence()).compareTo(existing.getConfidence()) > 0) {
            existing.setConfidence(confidence(draft.confidence()));
        }
        if (draft.summary() != null && StringUtils.hasText(draft.summary())) {
            existing.setSummary(draft.summary().trim());
        }
        existing.setUpdatedAt(now);
        return personal
                .update(existing)
                .then(decisions.insert(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        existing.getId(),
                        "duplicate",
                        1.0,
                        null,
                        null,
                        1.0,
                        Map.of("source", "content_hash"),
                        null,
                        now))
                .thenReturn(existing);
    }

    private Mono<MemoryPersonalEntity> publishUpdated(
            RequestIdentity identity, MemoryPersonalEntity entity) {
        return events
                .publish(
                        identity,
                        MemoryEvents.UPDATED,
                        entity.getId(),
                        entity.getLayer(),
                        entity.getStatus(),
                        Map.of("memoryType", entity.getMemoryType()))
                .thenReturn(entity);
    }

    private Mono<MemoryPersonalEntity> require(RequestIdentity identity, UUID id) {
        return personal
                .findOwned(id, identity.ownerId(), identity.workspaceId())
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.NOT_FOUND, "memory-not-found", "记忆不存在。")));
    }

    private MemoryPersonalEntity baseEntity(RequestIdentity identity) {
        var entity = new MemoryPersonalEntity();
        entity.setId(UUID.randomUUID());
        entity.setOwnerId(identity.ownerId());
        entity.setWorkspaceId(identity.workspaceId());
        return entity;
    }

    private static String normalizeLayer(String value, boolean nullable) {
        if (!StringUtils.hasText(value)) {
            if (nullable) {
                return null;
            }
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-layer-invalid", "记忆层级不能为空。");
        }
        var layer = value.trim().toUpperCase();
        if (!MemoryText.PERSONAL_LAYERS.contains(layer)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-layer-invalid",
                    "个人记忆只允许 L1、L2 或 L3。");
        }
        return layer;
    }

    private static String normalizeStatus(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        var status = value.trim().toLowerCase();
        if (!STATUSES.contains(status)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-status-invalid", "记忆状态不合法。");
        }
        return status;
    }

    private static String memoryType(String value) {
        var type = MemoryText.required(
                value, "memory-type-required", "记忆类型不能为空。").toLowerCase();
        if (!MemoryText.MEMORY_TYPES.contains(type)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-type-invalid", "记忆类型不合法。");
        }
        return type;
    }

    private static String text(String value, String code, String message) {
        return MemoryText.required(value, code, message);
    }

    private static void validateContent(String content) {
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-content-too-long",
                    "记忆内容超过允许长度。");
        }
    }

    private static BigDecimal confidence(BigDecimal value) {
        var resolved = value == null ? new BigDecimal("0.6") : value;
        if (resolved.compareTo(MIN_CONFIDENCE) < 0
                || resolved.compareTo(MAX_CONFIDENCE) > 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-confidence-invalid",
                    "记忆置信度必须在 0 到 1 之间。");
        }
        return resolved;
    }

    private static void validateValidity(Instant validFrom, Instant validTo) {
        if (validTo != null && validFrom != null && !validTo.isAfter(validFrom)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-validity-invalid",
                    "记忆失效时间必须晚于生效时间。");
        }
    }

    private static int limit(int requestedLimit) {
        if (requestedLimit <= 0) {
            return 20;
        }
        return Math.min(requestedLimit, MAX_LIMIT);
    }

    private static List<Map<String, Object>> copyEvidence(List<Map<String, Object>> evidence) {
        return evidence.stream()
                .filter(entry -> entry != null && !entry.isEmpty())
                .map(entry -> (Map<String, Object>) new LinkedHashMap<String, Object>(entry))
                .toList();
    }

    private static List<Map<String, Object>> mergedEvidence(
            List<Map<String, Object>> existing,
            List<Map<String, Object>> incoming,
            Map<String, Object> extra) {
        var merged = new ArrayList<Map<String, Object>>();
        if (existing != null) {
            merged.addAll(copyEvidence(existing));
        }
        if (incoming != null) {
            merged.addAll(copyEvidence(incoming));
        }
        if (extra != null && !extra.isEmpty()) {
            merged.add(new LinkedHashMap<>(extra));
        }
        return merged;
    }

    private static List<String> mergedStrings(List<String> existing, List<String> incoming) {
        var values = new LinkedHashSet<String>();
        if (existing != null) {
            existing.stream().filter(StringUtils::hasText).map(String::trim).forEach(values::add);
        }
        if (incoming != null) {
            incoming.stream().filter(StringUtils::hasText).map(String::trim).forEach(values::add);
        }
        return List.copyOf(values);
    }

    private static Map<String, Object> correctionEvidence(UUID previousId, String reason) {
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("type", "correction");
        evidence.put("previousMemoryId", previousId.toString());
        if (StringUtils.hasText(reason)) {
            evidence.put("reason", reason.trim());
        }
        return evidence;
    }

    private static ApiException notActive() {
        return new ApiException(
                HttpStatus.CONFLICT,
                "memory-not-active",
                "只有生效中的记忆可以修改、修正或撤回。");
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    void setClock(Clock clock) {
        this.clock = clock;
    }

    private record MemoryDraft(
            String layer,
            String memoryType,
            String canonicalKey,
            String content,
            String summary,
            List<Map<String, Object>> evidence,
            List<String> sourceIds,
            BigDecimal confidence,
            Instant validFrom,
            Instant validTo) {}

    public record ExtractedMemory(
            String layer,
            String memoryType,
            String canonicalKey,
            String content,
            String summary,
            List<Map<String, Object>> evidence,
            List<String> sourceIds,
            BigDecimal confidence) {}
}
