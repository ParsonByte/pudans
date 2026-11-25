package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.contract.agent.MemorySource;
import cn.tolim.pudans.contract.agent.dto.MemoryRetrievalResult;
import cn.tolim.pudans.contract.agent.dto.MemoryRetrieveRequest;
import cn.tolim.pudans.contract.agent.dto.MemoryRetrievedItem;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryRetrievalLogRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemorySourceRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.vector.MemoryVectorRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryRetrieval {

    private static final int DEFAULT_MAX_ITEMS = 10;

    private static final int MAX_ITEMS = 50;

    private static final int DEFAULT_MAX_TOKENS = 2_000;

    private static final int MAX_TOKENS = 8_000;

    private static final int MAX_QUERY_LENGTH = 4_000;

    private final MemoryPersonalRepository personal;

    private final MemoryPoolRepository pool;

    private final MemorySourceRepository sources;

    private final MemoryVectorRetrieval vectors;

    private final MemoryRetrievalLogRepository logs;

    public Mono<MemoryRetrievalResult> retrieve(
            RequestIdentity identity, MemoryRetrieveRequest request) {
        if (request == null) {
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "memory-retrieve-body-required", "检索请求不能为空。"));
        }
        var query = query(request.query());
        var maxItems = maxItems(request.maxItems());
        var maxTokens = maxTokens(request.maxTokens());
        var layers = layers(request.layers());
        var started = Instant.now();
        var candidateLimit = Math.min(80, Math.max(20, maxItems * 4));
        return Mono.zip(
                        pool.publishedLexical(query, candidateLimit).collectList(),
                        personal.activeLexical(
                                        identity.ownerId(),
                                        identity.workspaceId(),
                                        query,
                                        candidateLimit)
                                .collectList(),
                        vectors.search(identity, query, candidateLimit))
                .flatMap(tuple -> complete(
                        identity,
                        request,
                        query,
                        maxItems,
                        maxTokens,
                        layers,
                        started,
                        tuple.getT1(),
                        tuple.getT2(),
                        tuple.getT3()));
    }

    private Mono<MemoryRetrievalResult> complete(
            RequestIdentity identity,
            MemoryRetrieveRequest request,
            String query,
            int maxItems,
            int maxTokens,
            Set<String> layers,
            Instant started,
            List<MemoryPoolEntity> poolLexical,
            List<MemoryPersonalEntity> personalLexical,
            MemoryVectorRetrieval.SearchResult vectorResult) {
        var poolIds = vectorResult.pool().stream()
                .map(MemoryVectorRepository.MemoryVectorHit::memoryId)
                .distinct()
                .toList();
        var personalIds = vectorResult.personal().stream()
                .map(MemoryVectorRepository.MemoryVectorHit::memoryId)
                .distinct()
                .toList();
        return Mono.zip(
                        pool.publishedByIds(poolIds).collectList(),
                        personal.activeByIds(identity.ownerId(), identity.workspaceId(), personalIds)
                                .collectList())
                .map(tuple -> {
                    var poolCandidates = candidates(
                            poolLexical, tuple.getT1(), vectorResult.pool(), query, layers, true);
                    var personalCandidates = candidates(
                            personalLexical,
                            tuple.getT2(),
                            vectorResult.personal(),
                            query,
                            layers,
                            false);
                    var merged = merge(poolCandidates, personalCandidates, vectorResult.available());
                    var selected = budget(merged, maxItems, maxTokens);
                    var warnings = new ArrayList<String>();
                    if (vectorResult.warning() != null) {
                        warnings.add(vectorResult.warning());
                    }
                    if (poolLexical.stream().anyMatch(item -> MemoryText.containsPii(
                            item.getGeneralizedContent()))) {
                        warnings.add("memory-pool-safety-filtered");
                    }
                    var estimatedTokens = selected.stream()
                            .mapToInt(candidate -> MemoryText.estimatedTokens(
                                    candidate.content() + " " + candidate.summary()))
                            .sum();
                    var items = selected.stream().map(MemoryRetrieval::item).toList();
                    return new MemoryRetrievalResult(
                            items,
                            estimatedTokens,
                            vectorResult.available(),
                            List.copyOf(warnings),
                            List.of());
                })
                .flatMap(result -> evidence(identity, request.includeEvidence())
                        .map(evidence -> new MemoryRetrievalResult(
                                result.items(),
                                result.estimatedTokens(),
                                result.vectorUsed(),
                                result.warnings(),
                                evidence)))
                .flatMap(result -> log(identity, query, result, started));
    }

    private List<Candidate> candidates(
            List<?> lexical,
            List<?> vectorItems,
            List<MemoryVectorRepository.MemoryVectorHit> vectorHits,
            String query,
            Set<String> layers,
            boolean poolNamespace) {
        var candidates = new LinkedHashMap<UUID, Candidate>();
        for (var item : lexical) {
            var candidate = candidate(item, poolNamespace);
            if (candidate != null && layers.contains(candidate.layer())) {
                candidates.put(candidate.id(), candidate);
            }
        }
        var vectorScores = vectorHits.stream()
                .collect(java.util.stream.Collectors.toMap(
                        MemoryVectorRepository.MemoryVectorHit::memoryId,
                        MemoryVectorRepository.MemoryVectorHit::score,
                        Math::max));
        for (var item : vectorItems) {
            var candidate = candidate(item, poolNamespace);
            if (candidate == null || !layers.contains(candidate.layer())) {
                continue;
            }
            var score = vectorScores.getOrDefault(candidate.id(), 0.0);
            candidates.put(candidate.id(), candidate.withVector(score));
        }
        return candidates.values().stream()
                .map(candidate -> candidate.withLexical(MemoryText.lexicalScore(
                        query, candidate.content(), candidate.summary())))
                .filter(candidate -> !poolNamespace || !MemoryText.containsPii(candidate.content()))
                .toList();
    }

    private static Candidate candidate(Object value, boolean poolNamespace) {
        if (poolNamespace && value instanceof MemoryPoolEntity entity) {
            var content = entity.getGeneralizedContent();
            if (content == null || content.isBlank()) {
                return null;
            }
            return new Candidate(
                    entity.getId(),
                    "pool",
                    entity.getLayer(),
                    entity.getMemoryType(),
                    content,
                    entity.getSummary(),
                    entity.getConfidence(),
                    entity.getValidFrom(),
                    entity.getValidTo(),
                    List.of(),
                    entity.getContentHash(),
                    entity.getCanonicalId(),
                    entity.getUpdatedAt(),
                    0,
                    0,
                    0);
        }
        if (!poolNamespace && value instanceof MemoryPersonalEntity entity) {
            return new Candidate(
                    entity.getId(),
                    "personal",
                    entity.getLayer(),
                    entity.getMemoryType(),
                    entity.getContent(),
                    entity.getSummary(),
                    entity.getConfidence(),
                    entity.getValidFrom(),
                    entity.getValidTo(),
                    entity.getSourceIds(),
                    entity.getContentHash(),
                    entity.getCanonicalId(),
                    entity.getUpdatedAt(),
                    0,
                    0,
                    0);
        }
        return null;
    }

    private static List<Candidate> merge(
            List<Candidate> poolCandidates,
            List<Candidate> personalCandidates,
            boolean vectorUsed) {
        var merged = new LinkedHashMap<String, Candidate>();
        var orderedPool = scored(poolCandidates, vectorUsed);
        var orderedPersonal = scored(personalCandidates, vectorUsed);
        for (var candidate : orderedPool) {
            merged.putIfAbsent(key(candidate), candidate);
        }
        for (var candidate : orderedPersonal) {
            var key = key(candidate);
            var existing = merged.get(key);
            if (existing == null || "pool".equals(existing.namespace())) {
                merged.put(key, candidate);
            }
        }
        return merged.values().stream()
                .sorted(Comparator.comparingDouble(Candidate::score).reversed()
                        .thenComparing(candidate -> candidate.id().toString()))
                .toList();
    }

    private static List<Candidate> scored(List<Candidate> candidates, boolean vectorUsed) {
        var now = Instant.now();
        return candidates.stream()
                .map(candidate -> {
                    var confidence = candidate.confidence() == null
                            ? 0
                            : candidate.confidence().doubleValue();
                    var freshness = MemoryText.freshness(candidate.updatedAt(), now);
                    double score;
                    if (vectorUsed) {
                        score = 0.6 * candidate.vectorScore()
                                + 0.25 * candidate.lexicalScore()
                                + 0.1 * confidence
                                + 0.05 * freshness;
                    } else {
                        score = 0.55 * candidate.lexicalScore()
                                + 0.3 * confidence
                                + 0.15 * freshness;
                    }
                    return candidate.withScore(score);
                })
                .sorted(Comparator.comparingDouble(Candidate::score).reversed())
                .toList();
    }

    private static List<Candidate> budget(
            List<Candidate> candidates, int maxItems, int maxTokens) {
        var pool = candidates.stream().filter(item -> "pool".equals(item.namespace())).toList();
        var personal = candidates.stream()
                .filter(item -> "personal".equals(item.namespace()))
                .toList();
        var poolQuota = maxItems <= 1 ? maxItems : Math.max(1, maxItems / 2);
        var selected = new ArrayList<Candidate>();
        var usedTokens = 0;
        usedTokens = append(pool, poolQuota, maxItems, maxTokens, usedTokens, selected);
        usedTokens = append(personal, maxItems, maxItems, maxTokens, usedTokens, selected);
        append(pool, maxItems, maxItems, maxTokens, usedTokens, selected);
        return selected;
    }

    private static int append(
            List<Candidate> source,
            int limit,
            int maxItems,
            int maxTokens,
            int usedTokens,
            List<Candidate> selected) {
        var added = 0;
        for (var candidate : source) {
            if (added >= limit || selected.size() >= maxItems) {
                break;
            }
            if (selected.stream().anyMatch(item -> item.id().equals(candidate.id()))) {
                continue;
            }
            var tokens = MemoryText.estimatedTokens(candidate.content() + " " + candidate.summary());
            if (!selected.isEmpty() && usedTokens + tokens > maxTokens) {
                continue;
            }
            selected.add(candidate);
            usedTokens += tokens;
            added++;
        }
        return usedTokens;
    }

    private Mono<List<MemorySource>> evidence(
            RequestIdentity identity, Boolean includeEvidence) {
        if (!Boolean.TRUE.equals(includeEvidence)) {
            return Mono.just(List.of());
        }
        return sources
                .recent(identity.ownerId(), identity.workspaceId(), 5)
                .map(entity -> entity.toContract())
                .collectList();
    }

    private Mono<MemoryRetrievalResult> log(
            RequestIdentity identity,
            String query,
            MemoryRetrievalResult result,
            Instant started) {
        var selectedIds = result.items().stream().map(MemoryRetrievedItem::memoryId).toList();
        var scores = result.items().stream().map(MemoryRetrievedItem::score).toList();
        var namespaces = result.items().stream()
                .map(MemoryRetrievedItem::namespace)
                .distinct()
                .toList();
        var latency = Duration.between(started, Instant.now()).toMillis();
        return logs.insert(
                        UUID.randomUUID(),
                        identity.ownerId(),
                        identity.workspaceId(),
                        "memory",
                        MemoryText.queryHash(query),
                        namespaces,
                        selectedIds,
                        scores,
                        Math.max(0, latency),
                        Instant.now())
                .onErrorResume(error -> {
                    log.warn("写入记忆检索日志失败", error);
                    return Mono.empty();
                })
                .thenReturn(result);
    }

    private static MemoryRetrievedItem item(Candidate candidate) {
        return new MemoryRetrievedItem(
                candidate.id(),
                candidate.namespace(),
                candidate.layer(),
                candidate.memoryType(),
                candidate.content(),
                candidate.summary(),
                candidate.confidence(),
                candidate.validFrom(),
                candidate.validTo(),
                candidate.score(),
                candidate.sourceIds());
    }

    private static String key(Candidate candidate) {
        if (candidate.contentHash() != null && !candidate.contentHash().isBlank()) {
            return "hash:" + candidate.contentHash();
        }
        if (candidate.canonicalId() != null) {
            return "canonical:" + candidate.canonicalId();
        }
        return "id:" + candidate.id();
    }

    private static String query(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-query-required",
                    "记忆检索内容不能为空。");
        }
        var query = value.trim();
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-query-too-long",
                    "记忆检索内容超过允许长度。");
        }
        return query;
    }

    private static int maxItems(Integer value) {
        var resolved = value == null ? DEFAULT_MAX_ITEMS : value;
        if (resolved <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-retrieve-limit-invalid",
                    "记忆条目上限必须大于零。");
        }
        return Math.min(resolved, MAX_ITEMS);
    }

    private static int maxTokens(Integer value) {
        var resolved = value == null ? DEFAULT_MAX_TOKENS : value;
        if (resolved <= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "memory-retrieve-token-limit-invalid",
                    "记忆 token 上限必须大于零。");
        }
        return Math.min(resolved, MAX_TOKENS);
    }

    private static Set<String> layers(Collection<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return MemoryText.PERSONAL_LAYERS;
        }
        var layers = new LinkedHashSet<String>();
        for (var value : requested) {
            var layer = value == null ? "" : value.trim().toUpperCase();
            if (!MemoryText.PERSONAL_LAYERS.contains(layer)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "memory-layer-invalid",
                        "检索层级只允许 L1、L2 或 L3。");
            }
            layers.add(layer);
        }
        return Set.copyOf(layers);
    }

    private record Candidate(
            UUID id,
            String namespace,
            String layer,
            String memoryType,
            String content,
            String summary,
            BigDecimal confidence,
            Instant validFrom,
            Instant validTo,
            List<String> sourceIds,
            String contentHash,
            UUID canonicalId,
            Instant updatedAt,
            double vectorScore,
            double lexicalScore,
            double score) {

        private Candidate withVector(double value) {
            return new Candidate(
                    id,
                    namespace,
                    layer,
                    memoryType,
                    content,
                    summary,
                    confidence,
                    validFrom,
                    validTo,
                    sourceIds,
                    contentHash,
                    canonicalId,
                    updatedAt,
                    value,
                    lexicalScore,
                    score);
        }

        private Candidate withLexical(double value) {
            return new Candidate(
                    id,
                    namespace,
                    layer,
                    memoryType,
                    content,
                    summary,
                    confidence,
                    validFrom,
                    validTo,
                    sourceIds,
                    contentHash,
                    canonicalId,
                    updatedAt,
                    vectorScore,
                    value,
                    score);
        }

        private Candidate withScore(double value) {
            return new Candidate(
                    id,
                    namespace,
                    layer,
                    memoryType,
                    content,
                    summary,
                    confidence,
                    validFrom,
                    validTo,
                    sourceIds,
                    contentHash,
                    canonicalId,
                    updatedAt,
                    vectorScore,
                    lexicalScore,
                    value);
        }
    }
}
