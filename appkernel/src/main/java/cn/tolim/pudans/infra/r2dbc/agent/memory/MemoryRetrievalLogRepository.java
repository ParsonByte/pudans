package cn.tolim.pudans.infra.r2dbc.agent.memory;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MemoryRetrievalLogRepository {

    private final DatabaseClient databaseClient;

    public Mono<Void> insert(
            UUID id,
            String ownerId,
            String workspaceId,
            String scope,
            String queryHash,
            List<String> namespaces,
            List<UUID> selectedIds,
            List<Double> scores,
            long latencyMs,
            Instant createdAt) {
        return databaseClient
                .sql("""
                        INSERT INTO memory_retrieval_logs (
                            id, owner_id, workspace_id, scope, query_hash, namespaces_jsonb,
                            selected_ids_jsonb, scores_jsonb, latency_ms, created_at)
                        VALUES (
                            :id, :ownerId, :workspaceId, :scope, :queryHash,
                            CAST(:namespaces AS jsonb), CAST(:selectedIds AS jsonb),
                            CAST(:scores AS jsonb), :latencyMs, :createdAt)
                        """)
                .bind("id", id)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("scope", scope)
                .bind("queryHash", queryHash)
                .bind("namespaces", MemoryJsonCodec.write(namespaces))
                .bind("selectedIds", MemoryJsonCodec.write(
                        selectedIds.stream().map(UUID::toString).toList()))
                .bind("scores", MemoryJsonCodec.write(scores))
                .bind("latencyMs", latencyMs)
                .bind("createdAt", utc(createdAt))
                .then();
    }
}
