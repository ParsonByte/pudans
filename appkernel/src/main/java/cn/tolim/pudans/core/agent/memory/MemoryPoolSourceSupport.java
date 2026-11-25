package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryLineageRepository;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryPoolRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class MemoryPoolSourceSupport {

    private static final int MIN_SOURCE_OWNERS = 2;

    private static final int MIN_SOURCE_WORKSPACES = 2;

    private final MemoryLineageRepository lineage;

    private final MemoryPoolRepository pool;

    public Mono<MemoryPoolEntity> refresh(UUID poolId) {
        return pool.findById(poolId).flatMap(entity -> lineage
                .support(poolId)
                .flatMap(support -> {
                    entity.setSupportCount(support.supportCount());
                    entity.setSourceOwnerCount(support.sourceOwnerCount());
                    entity.setSourceWorkspaceCount(support.sourceWorkspaceCount());
                    entity.setSourceWorkspaceIds(support.sourceWorkspaceIds() == null
                            ? List.of()
                            : List.copyOf(support.sourceWorkspaceIds()));
                    if (insufficient(support.sourceOwnerCount(), support.sourceWorkspaceCount())) {
                        if ("published".equals(entity.getStatus())
                                || "approved".equals(entity.getStatus())) {
                            entity.setStatus("superseded");
                            entity.setValidTo(Instant.now());
                        } else if ("candidate".equals(entity.getStatus())) {
                            entity.setStatus("rejected");
                        }
                    }
                    entity.setUpdatedAt(Instant.now());
                    return pool.update(entity);
                }));
    }

    public Mono<Void> afterSourcesChanged(Collection<String> sourceIds) {
        if (sourceIds == null || sourceIds.isEmpty()) {
            return Mono.empty();
        }
        return lineage
                .memoryIdsForSources(sourceIds.stream().toList())
                .flatMapMany(Flux::fromIterable)
                .concatMap(this::refresh)
                .then();
    }

    public static boolean insufficient(int sourceOwnerCount, int sourceWorkspaceCount) {
        return sourceOwnerCount < MIN_SOURCE_OWNERS
                || sourceWorkspaceCount < MIN_SOURCE_WORKSPACES;
    }
}
