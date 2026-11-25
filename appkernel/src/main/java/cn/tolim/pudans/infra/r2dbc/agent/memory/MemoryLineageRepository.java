package cn.tolim.pudans.infra.r2dbc.agent.memory;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MemoryLineageRepository {

    private final DatabaseClient databaseClient;

    public record MemoryPoolSupport(
            int supportCount,
            int sourceOwnerCount,
            int sourceWorkspaceCount,
            List<String> sourceWorkspaceIds) {}

    public Mono<Void> add(
            UUID id,
            UUID memoryId,
            String memoryLayer,
            String sourceType,
            String sourceId,
            String relation,
            Instant createdAt) {
        return databaseClient
                .sql("""
                        INSERT INTO memory_lineage (
                            id, memory_id, memory_layer, source_type, source_id, relation,
                            created_at)
                        VALUES (
                            :id, :memoryId, :memoryLayer, :sourceType, :sourceId, :relation,
                            :createdAt)
                        """)
                .bind("id", id)
                .bind("memoryId", memoryId)
                .bind("memoryLayer", memoryLayer)
                .bind("sourceType", sourceType)
                .bind("sourceId", sourceId)
                .bind("relation", relation)
                .bind("createdAt", cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc(createdAt))
                .then();
    }

    public Flux<String> sourceIds(UUID memoryId) {
        return databaseClient
                .sql("""
                        SELECT DISTINCT source_id
                          FROM memory_lineage
                         WHERE memory_id = :memoryId
                           AND memory_layer = 'L4'
                           AND source_type = 'personal_memory'
                         ORDER BY source_id
                        """)
                .bind("memoryId", memoryId)
                .map((row, metadata) -> row.get("source_id", String.class))
                .all();
    }

    public Flux<UUID> poolMemoryIdsBySource(String sourceId) {
        return databaseClient
                .sql("""
                        SELECT DISTINCT memory_id
                          FROM memory_lineage
                         WHERE memory_layer = 'L4'
                           AND source_type = 'personal_memory'
                           AND source_id = :sourceId
                        """)
                .bind("sourceId", sourceId)
                .map((row, metadata) -> row.get("memory_id", UUID.class))
                .all();
    }

    public Mono<MemoryPoolSupport> support(UUID memoryId) {
        return databaseClient
                .sql("""
                        SELECT COUNT(DISTINCT source.id) AS support_count,
                               COUNT(DISTINCT source.owner_id) AS source_owner_count,
                               COUNT(DISTINCT source.workspace_id) AS source_workspace_count,
                               COALESCE(
                                       jsonb_agg(DISTINCT source.workspace_id),
                                       '[]'::jsonb)::text AS source_workspace_ids
                          FROM memory_lineage lineage
                          JOIN personal_memory_items source
                            ON source.id::text = lineage.source_id
                         WHERE lineage.memory_id = :memoryId
                           AND lineage.memory_layer = 'L4'
                           AND lineage.source_type = 'personal_memory'
                           AND source.status = 'active'
                           AND (source.valid_to IS NULL OR source.valid_to > now())
                        """)
                .bind("memoryId", memoryId)
                .map((row, metadata) -> new MemoryPoolSupport(
                        intValue(row.get("support_count", Long.class)),
                        intValue(row.get("source_owner_count", Long.class)),
                        intValue(row.get("source_workspace_count", Long.class)),
                        MemoryJsonCodec.readStrings(
                                row.get("source_workspace_ids", String.class))))
                .one()
                .defaultIfEmpty(new MemoryPoolSupport(0, 0, 0, List.of()));
    }

    private static int intValue(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    public Mono<List<UUID>> memoryIdsForSources(List<String> sourceIds) {
        if (sourceIds.isEmpty()) {
            return Mono.just(List.of());
        }
        return databaseClient
                .sql("""
                        SELECT DISTINCT memory_id
                          FROM memory_lineage
                         WHERE memory_layer = 'L4'
                           AND source_type = 'personal_memory'
                           AND source_id IN (:sourceIds)
                        """)
                .bind("sourceIds", sourceIds)
                .map((row, metadata) -> row.get("memory_id", UUID.class))
                .all()
                .collectList();
    }
}
