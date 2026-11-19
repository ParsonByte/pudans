package cn.tolim.pudans.infra.r2dbc.ai.memory;

import cn.tolim.pudans.core.ai.memory.L2SceneBlock;
import cn.tolim.pudans.core.ai.memory.MemoryType;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
@RequiredArgsConstructor
public class AiMemoryRecallRepository {

    private final DatabaseClient databaseClient;

    public Flux<RecalledMemory> searchL1(String teamId, String agentId, String userId, String query, int limit) {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(agentId, "agentId");
        Objects.requireNonNull(userId, "userId");
        if (limit <= 0) {
            return Flux.empty();
        }
        var hasQuery = query != null && !query.isBlank();
        var normalizedQuery = hasQuery ? query.strip() : "";
        return databaseClient.sql("""
                        SELECT id, record_id, content, memory_type, scene_name, priority, version, updated_at,
                               CAST((
                                   CASE WHEN :hasQuery AND content ILIKE :keywordPattern ESCAPE '\\'
                                        THEN 0.6 ELSE 0.0 END
                                   + GREATEST(similarity(content, :query), 0.0) * 0.25
                                   + (priority / 100.0) * 0.1
                                   + (decay_score / 100.0) * 0.05
                               ) AS double precision) AS score
                        FROM ai_memory_l1
                        WHERE team_id = :teamId
                          AND agent_id = :agentId
                          AND status = 'ACTIVE'
                          AND (user_id = :userId OR scope = 'TEAM')
                        ORDER BY score DESC, updated_at DESC, id
                        LIMIT :limit
                        """)
                .bind("hasQuery", hasQuery)
                .bind("keywordPattern", likePattern(normalizedQuery))
                .bind("query", normalizedQuery)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("userId", userId)
                .bind("limit", limit)
                .map((row, metadata) -> mapRecalled(row))
                .all();
    }

    public Flux<L2SceneBlock> findScenes(String teamId, String agentId, int limit) {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(agentId, "agentId");
        if (limit <= 0) {
            return Flux.empty();
        }
        return databaseClient.sql("""
                        SELECT id, team_id, agent_id, scene_key, title, summary, l1_ids, heat, confidence,
                               last_active_at
                        FROM ai_memory_l2
                        WHERE team_id = :teamId AND agent_id = :agentId
                        ORDER BY heat DESC, last_active_at DESC, id
                        LIMIT :limit
                        """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("limit", limit)
                .map((row, metadata) -> AiMemoryL2Repository.mapBlock(row))
                .all();
    }

    private static RecalledMemory mapRecalled(Row row) {
        return new RecalledMemory(
                row.get("id", Long.class),
                row.get("record_id", String.class),
                row.get("content", String.class),
                MemoryType.valueOf(row.get("memory_type", String.class)),
                row.get("scene_name", String.class),
                row.get("priority", Integer.class),
                row.get("version", Integer.class),
                row.get("updated_at", Instant.class),
                row.get("score", Double.class));
    }

    private static String likePattern(String query) {
        var escaped = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }

    public record RecalledMemory(
            Long id,
            String recordId,
            String content,
            MemoryType type,
            String sceneName,
            int priority,
            int version,
            Instant updatedAt,
            double score) {

        public RecalledMemory {
            Objects.requireNonNull(recordId, "recordId");
            Objects.requireNonNull(content, "content");
            Objects.requireNonNull(type, "type");
            sceneName = sceneName == null ? "" : sceneName;
            Objects.requireNonNull(updatedAt, "updatedAt");
            if (!Double.isFinite(score)) {
                throw new IllegalArgumentException("score 必须是有限数值");
            }
        }
    }
}
