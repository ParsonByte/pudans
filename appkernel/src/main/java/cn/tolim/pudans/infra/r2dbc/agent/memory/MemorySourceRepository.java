package cn.tolim.pudans.infra.r2dbc.agent.memory;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MemorySourceRepository {

    private static final String COLUMNS = """
            SELECT id, owner_id, workspace_id, source_kind, source_id, session_id, run_id,
                   task_id, message_start_id, message_end_id, occurred_at, content_summary,
                   content_digest, status, metadata_jsonb::text AS metadata, created_at
              FROM memory_sources
            """;

    private final DatabaseClient databaseClient;

    public Mono<MemorySourceEntity> findSource(
            String ownerId, String workspaceId, String sourceKind, String sourceId) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE owner_id = :ownerId
                           AND workspace_id = :workspaceId
                           AND source_kind = :sourceKind
                           AND source_id = :sourceId
                         ORDER BY created_at DESC
                         LIMIT 1
                        """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("sourceKind", sourceKind)
                .bind("sourceId", sourceId)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<MemorySourceEntity> recent(String ownerId, String workspaceId, int limit) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE owner_id = :ownerId
                           AND workspace_id = :workspaceId
                         ORDER BY occurred_at DESC
                         LIMIT :limit
                        """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<MemorySourceEntity> insert(MemorySourceEntity entity) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO memory_sources (
                            id, owner_id, workspace_id, source_kind, source_id, session_id,
                            run_id, task_id, message_start_id, message_end_id, occurred_at,
                            content_summary, content_digest, status, metadata_jsonb, created_at)
                        VALUES (
                            :id, :ownerId, :workspaceId, :sourceKind, :sourceId, :sessionId,
                            :runId, :taskId, :messageStartId, :messageEndId, :occurredAt,
                            :contentSummary, :contentDigest, :status, CAST(:metadata AS jsonb),
                            :createdAt)
                        """)
                .bind("id", entity.getId())
                .bind("ownerId", entity.getOwnerId())
                .bind("workspaceId", entity.getWorkspaceId())
                .bind("sourceKind", entity.getSourceKind())
                .bind("sourceId", entity.getSourceId())
                .bind("sessionId", entity.getSessionId())
                .bind("runId", entity.getRunId());
        spec = bindNullable(spec, "taskId", entity.getTaskId(), UUID.class);
        return spec
                .bind("messageStartId", entity.getMessageStartId())
                .bind("messageEndId", entity.getMessageEndId())
                .bind("occurredAt", utc(entity.getOccurredAt()))
                .bind("contentSummary", entity.getContentSummary())
                .bind("contentDigest", entity.getContentDigest())
                .bind("status", entity.getStatus())
                .bind("metadata", MemoryJsonCodec.write(entity.getMetadata()))
                .bind("createdAt", utc(entity.getCreatedAt()))
                .then()
                .thenReturn(entity);
    }

    static MemorySourceEntity map(io.r2dbc.spi.Row row) {
        var entity = new MemorySourceEntity();
        entity.setId(row.get("id", UUID.class));
        entity.setOwnerId(row.get("owner_id", String.class));
        entity.setWorkspaceId(row.get("workspace_id", String.class));
        entity.setSourceKind(row.get("source_kind", String.class));
        entity.setSourceId(row.get("source_id", String.class));
        entity.setSessionId(row.get("session_id", UUID.class));
        entity.setRunId(row.get("run_id", UUID.class));
        entity.setTaskId(row.get("task_id", UUID.class));
        entity.setMessageStartId(row.get("message_start_id", UUID.class));
        entity.setMessageEndId(row.get("message_end_id", UUID.class));
        entity.setOccurredAt(instant(row.get("occurred_at", OffsetDateTime.class)));
        entity.setContentSummary(row.get("content_summary", String.class));
        entity.setContentDigest(row.get("content_digest", String.class));
        entity.setStatus(row.get("status", String.class));
        entity.setMetadata(MemoryJsonCodec.readObject(row.get("metadata", String.class)));
        entity.setCreatedAt(instant(row.get("created_at", OffsetDateTime.class)));
        return entity;
    }
}
