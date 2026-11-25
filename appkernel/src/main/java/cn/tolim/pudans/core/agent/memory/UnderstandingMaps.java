package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.contract.agent.dto.UnderstandingMap;
import cn.tolim.pudans.contract.agent.dto.UnderstandingMapEntry;
import cn.tolim.pudans.contract.agent.dto.UnderstandingStatus;
import cn.tolim.pudans.core.agent.runtime.AgentModelRouteResolver;
import cn.tolim.pudans.core.ai.embedding.MemoryEmbeddingModels;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalRepository;
import cn.tolim.pudans.infra.r2dbc.ai.catalog.CatalogEntryRepository;
import cn.tolim.pudans.infra.r2dbc.support.PgVectorCapabilityProbe;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class UnderstandingMaps {

    private static final String DIMENSION_KIND = "understanding_dimension";

    private static final String EMPTY_STATE = "暂无相关记忆。";

    private static final String EXTRACTION_READY = "memory-extraction-ready";

    private static final String VECTOR_READY = "memory-vector-ready";

    private final CatalogEntryRepository entries;

    private final MemoryPersonalRepository memories;

    private final AgentModelRouteResolver routes;

    private final PgVectorCapabilityProbe capabilityProbe;

    private final MemoryEmbeddingModels embeddingModels;

    private Clock clock = Clock.systemUTC();

    // 地图只聚合当前用户和工作区内真实有效的个人记忆。
    public Mono<UnderstandingMap> map(RequestIdentity identity) {
        var now = Instant.now(clock);
        return Mono.zip(
                        entries.findActiveByKind(DIMENSION_KIND, now).collectList(),
                        memories.statsOwned(identity.ownerId(), identity.workspaceId())
                                .collectMap(MemoryPersonalRepository.MemoryTypeStat::memoryType))
                .map(tuple -> {
                    var stats = tuple.getT2();
                    var dimensions = tuple.getT1().stream()
                            .map(entry -> {
                                var stat = stats.get(entry.entryKey());
                                return new UnderstandingMapEntry(
                                        entry.entryKey(),
                                        entry.label(),
                                        entry.description(),
                                        stat == null ? 0 : stat.memoryCount(),
                                        stat == null
                                                ? null
                                                : stat.averageConfidence() == null
                                                        ? null
                                                        : stat.averageConfidence().stripTrailingZeros(),
                                        stat == null ? null : stat.lastUpdatedAt(),
                                        emptyState(entry.metadata()),
                                        entry.metadata() == null ? Map.of() : entry.metadata());
                            })
                            .toList();
                    var total = dimensions.stream().mapToInt(UnderstandingMapEntry::memoryCount).sum();
                    return new UnderstandingMap(dimensions, total, now);
                });
    }

    // 状态同时探测真实抽取模型与默认 Embedding 向量空间，不把配置缺失报成可用。
    public Mono<UnderstandingStatus> status(RequestIdentity identity) {
        var now = Instant.now(clock);
        return Mono.zip(extraction(identity.workspaceId()), vector(identity.workspaceId()))
                .map(tuple -> new UnderstandingStatus(
                        tuple.getT1().configured(),
                        tuple.getT2().configured(),
                        tuple.getT1().reasonCode(),
                        tuple.getT2().reasonCode(),
                        now));
    }

    private Mono<Probe> extraction(String workspaceId) {
        return routes
                .resolve(workspaceId, null)
                .map(route -> new Probe(true, EXTRACTION_READY))
                .onErrorResume(error -> Mono.just(new Probe(false, reason(error,
                        "understanding-extraction-probe-failed"))));
    }

    private Mono<Probe> vector(String workspaceId) {
        return capabilityProbe
                .probe()
                .flatMap(capability -> {
                    if (!capability.usable()) {
                        return Mono.just(new Probe(false, capability.reasonCode()));
                    }
                    return embeddingModels
                            .defaultSpace(workspaceId)
                            .map(space -> new Probe(true, VECTOR_READY))
                            .onErrorResume(error -> Mono.just(new Probe(false, reason(error,
                                    "understanding-vector-probe-failed"))));
                })
                .onErrorResume(error -> Mono.just(new Probe(false, reason(error,
                        "understanding-vector-probe-failed"))));
    }

    private static String reason(Throwable error, String fallback) {
        return error instanceof ApiException api && StringUtils.hasText(api.getCode())
                ? api.getCode()
                : fallback;
    }

    private static String emptyState(Map<String, Object> metadata) {
        if (metadata != null && metadata.get("emptyState") instanceof String value
                && StringUtils.hasText(value)) {
            return value.trim();
        }
        return EMPTY_STATE;
    }

    private record Probe(boolean configured, String reasonCode) {}

    void setClock(Clock clock) {
        this.clock = clock;
    }
}
