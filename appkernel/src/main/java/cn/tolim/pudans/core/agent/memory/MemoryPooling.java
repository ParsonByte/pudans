package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryLineageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPersonalRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryPooling {

    private static final int MAX_SCAN = 1_000;

    private final MemoryPersonalRepository personal;

    private final MemoryPoolRepository pool;

    private final MemoryLineageRepository lineage;

    private final MemoryEvents events;

    private final MemoryAudit audit;

    public record ConsolidationStats(int scanned, int created, int updated, int skipped) {}

    public Mono<ConsolidationStats> consolidate() {
        return personal
                .activeForPooling(MAX_SCAN)
                .collectList()
                .flatMap(sources -> {
                    var groups = groups(sources);
                    var stats = new Stats(sources.size());
                    return Flux.fromIterable(groups)
                            .concatMap(group -> consolidateGroup(group, stats))
                            .then(Mono.fromSupplier(stats::result));
                });
    }

    private Mono<Void> consolidateGroup(Group group, Stats stats) {
        if (group.sources().isEmpty()
                || MemoryText.containsPii(group.generalizedContent())
                || MemoryText.containsPii(group.canonicalKey())
                || (group.summary() != null && MemoryText.containsPii(group.summary()))) {
            stats.skipped++;
            return Mono.empty();
        }
        return pool
                .findCurrentByHash(group.contentHash())
                .flatMap(existing -> mergeExisting(existing, group, stats))
                .switchIfEmpty(Mono.defer(() -> createCandidate(group, stats)));
    }

    private Mono<Void> mergeExisting(MemoryPoolEntity existing, Group group, Stats stats) {
        return lineage
                .sourceIds(existing.getId())
                .collectList()
                .flatMap(existingSourceIds -> {
                    var existingIds = new LinkedHashSet<>(existingSourceIds);
                    return Flux.fromIterable(group.sources())
                            .filter(source -> !existingIds.contains(source.getId().toString()))
                            .concatMap(source -> lineage.add(
                                    UUID.randomUUID(),
                                    existing.getId(),
                                    "L4",
                                    "personal_memory",
                                    source.getId().toString(),
                                    "supports",
                                    Instant.now()))
                            .then(Mono.defer(() -> {
                                existing.setSupportCount(group.sources().size());
                                existing.setSourceOwnerCount(group.ownerCount());
                                existing.setSourceWorkspaceCount(group.workspaceCount());
                                existing.setSourceWorkspaceIds(group.workspaceIds());
                                existing.setUpdatedAt(Instant.now());
                                stats.updated++;
                                return pool.update(existing).then();
                            }));
                });
    }

    private Mono<Void> createCandidate(Group group, Stats stats) {
        var now = Instant.now();
        var entity = new MemoryPoolEntity();
        entity.setId(UUID.randomUUID());
        entity.setScope("global");
        entity.setLayer("L4");
        entity.setMemoryType(group.sources().getFirst().getMemoryType());
        entity.setTitle(group.canonicalKey());
        entity.setContent(group.generalizedContent());
        entity.setSummary(group.summary());
        entity.setGeneralizedContent(group.generalizedContent());
        entity.setSupportCount(group.sources().size());
        entity.setSourceOwnerCount(group.ownerCount());
        entity.setSourceWorkspaceCount(group.workspaceCount());
        entity.setSourceWorkspaceIds(group.workspaceIds());
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("canonicalKey", group.canonicalKey());
        evidence.put("contentHash", group.contentHash());
        evidence.put("sourceCount", group.sources().size());
        entity.setEvidenceSummary(evidence);
        entity.setSafetyClass("general");
        entity.setConfidence(averageConfidence(group.sources()));
        entity.setStatus("candidate");
        entity.setVisibility("global");
        entity.setValidFrom(now);
        entity.setContentHash(group.contentHash());
        entity.setSimhash(MemoryText.simhash(group.generalizedContent()));
        entity.setCanonicalId(entity.getId());
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        return pool.insert(entity)
                .flatMap(saved -> Flux.fromIterable(group.sources())
                        .concatMap(source -> lineage.add(
                                UUID.randomUUID(),
                                saved.getId(),
                                "L4",
                                "personal_memory",
                                source.getId().toString(),
                                "supports",
                                now))
                        .then(Mono.defer(() -> {
                            stats.created++;
                            return events.publishPoolCandidate(
                                    saved.getId(),
                                    Map.of(
                                            "supportCount", saved.getSupportCount(),
                                            "sourceOwnerCount", saved.getSourceOwnerCount(),
                                            "sourceWorkspaceCount",
                                                    saved.getSourceWorkspaceCount()))
                                    .doOnSuccess(ignored -> audit.background(
                                            "system",
                                            "MEMORY_POOL_CANDIDATE_CREATED",
                                            "MemoryPoolItem",
                                            saved.getId(),
                                            true,
                                            Map.of(
                                                    "supportCount", saved.getSupportCount(),
                                                    "sourceOwnerCount",
                                                            saved.getSourceOwnerCount(),
                                                    "sourceWorkspaceCount",
                                                            saved.getSourceWorkspaceCount())));
                        })))
                .then();
    }

    private static List<Group> groups(List<MemoryPersonalEntity> sources) {
        var grouped = new LinkedHashMap<String, List<MemoryPersonalEntity>>();
        for (var source : sources) {
            if (source.getCanonicalKey() == null || source.getContentHash() == null) {
                continue;
            }
            var key = source.getCanonicalKey() + "\u0000" + source.getContentHash();
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(source);
        }
        var groups = new ArrayList<Group>();
        for (var entries : grouped.values()) {
            var ownerCount = (int) entries.stream()
                    .map(MemoryPersonalEntity::getOwnerId)
                    .distinct()
                    .count();
            var workspaceIds = entries.stream()
                    .map(MemoryPersonalEntity::getWorkspaceId)
                    .distinct()
                    .sorted()
                    .toList();
            if (MemoryPoolSourceSupport.insufficient(ownerCount, workspaceIds.size())) {
                continue;
            }
            var first = entries.getFirst();
            var summary = entries.stream()
                    .map(MemoryPersonalEntity::getSummary)
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .orElse(null);
            groups.add(new Group(
                    first.getCanonicalKey(),
                    first.getContentHash(),
                    first.getContent().strip(),
                    summary,
                    List.copyOf(entries),
                    ownerCount,
                    workspaceIds.size(),
                    workspaceIds));
        }
        return groups;
    }

    private static BigDecimal averageConfidence(List<MemoryPersonalEntity> sources) {
        var total = sources.stream()
                .map(MemoryPersonalEntity::getConfidence)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(sources.size()), 4, RoundingMode.HALF_UP);
    }

    private record Group(
            String canonicalKey,
            String contentHash,
            String generalizedContent,
            String summary,
            List<MemoryPersonalEntity> sources,
            int ownerCount,
            int workspaceCount,
            List<String> workspaceIds) {}

    private static final class Stats {

        private final int scanned;

        private int created;

        private int updated;

        private int skipped;

        private Stats(int scanned) {
            this.scanned = scanned;
        }

        private ConsolidationStats result() {
            return new ConsolidationStats(scanned, created, updated, skipped);
        }
    }
}
