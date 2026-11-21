package cn.tolim.pudans.core.infra.r2dbc.ai.memory;

import cn.tolim.pudans.core.ai.memory.asset.MemoryLayerCounts;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiMessageRepository.Message;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiR2dbcBindings;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiMemoryAssetRepository {

    private final DatabaseClient databaseClient;

    public Flux<ScopeRow> findScopes(@Nullable String teamId) {
        var spec = databaseClient.sql("""
                        SELECT team_id, user_id, agent_id
                        FROM ai_message
                        WHERE deleted_at IS NULL
                          AND (:teamId IS NULL OR team_id = :teamId)
                        UNION
                        SELECT team_id, user_id, agent_id
                        FROM ai_memory_l1
                        WHERE status = 'ACTIVE'
                          AND (:teamId IS NULL OR team_id = :teamId)
                        ORDER BY team_id, agent_id, user_id
                        """);
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", teamId, String.class);
        return spec.map((row, metadata) -> new ScopeRow(
                        row.get("team_id", String.class),
                        row.get("user_id", String.class),
                        row.get("agent_id", String.class)))
                .all();
    }

    public Flux<AgentScopeRow> findAgentScopes(@Nullable String teamId) {
        var spec = databaseClient.sql("""
                        SELECT team_id, agent_id
                        FROM ai_memory_l2
                        WHERE (:teamId IS NULL OR team_id = :teamId)
                        UNION
                        SELECT team_id, agent_id
                        FROM ai_memory_l3
                        WHERE (:teamId IS NULL OR team_id = :teamId)
                        ORDER BY team_id, agent_id
                        """);
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", teamId, String.class);
        return spec.map((row, metadata) -> new AgentScopeRow(
                        row.get("team_id", String.class),
                        row.get("agent_id", String.class)))
                .all();
    }

    public Mono<MemoryLayerCounts> counts(String teamId, String userId, String agentId) {
        return databaseClient.sql("""
                        SELECT
                            (SELECT COUNT(*) FROM ai_message
                             WHERE team_id = :teamId AND user_id = :userId AND agent_id = :agentId
                               AND deleted_at IS NULL) AS l0,
                            (SELECT COUNT(*) FROM ai_memory_l1
                             WHERE team_id = :teamId AND user_id = :userId AND agent_id = :agentId
                               AND status = 'ACTIVE') AS l1,
                            (SELECT COUNT(*) FROM ai_memory_l2
                             WHERE team_id = :teamId AND agent_id = :agentId) AS l2,
                            (SELECT COUNT(*) FROM ai_memory_l3
                             WHERE team_id = :teamId AND agent_id = :agentId) AS l3
                        """)
                .bind("teamId", teamId)
                .bind("userId", userId)
                .bind("agentId", agentId)
                .map((row, metadata) -> new MemoryLayerCounts(
                        row.get("l0", Long.class),
                        row.get("l1", Long.class),
                        row.get("l2", Long.class),
                        row.get("l3", Long.class)))
                .one();
    }

    public Flux<Message> listMessages(
            String teamId,
            String userId,
            String agentId,
            long beforeId,
            @Nullable Instant from,
            @Nullable Instant to,
            @Nullable String query,
            int limit) {
        var spec = databaseClient.sql("""
                        SELECT id, conversation_id, space_id, user_id, team_id, agent_id, session_id,
                               task_id, request_id, turn_index, message_index, role, content,
                               content_type, token_count, model_code, created_at
                        FROM ai_message
                        WHERE team_id = :teamId
                          AND user_id = :userId
                          AND agent_id = :agentId
                          AND deleted_at IS NULL
                          AND id < :beforeId
                          AND (:from IS NULL OR created_at >= :from)
                          AND (:to IS NULL OR created_at < :to)
                          AND (:pattern IS NULL OR content ILIKE :pattern ESCAPE '\\')
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("teamId", teamId)
                .bind("userId", userId)
                .bind("agentId", agentId)
                .bind("beforeId", beforeId)
                .bind("limit", limit);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "from",
                from == null ? null : OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.class);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "to",
                to == null ? null : OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                OffsetDateTime.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "pattern", query == null ? null : AiMemorySql.likePattern(query), String.class);
        return spec.map((row, metadata) -> mapMessage(row)).all();
    }

    public Mono<Long> deleteMessages(
            String teamId, String userId, String agentId, List<Long> messageIds, List<String> sessionIds) {
        return databaseClient.sql("""
                        UPDATE ai_message
                        SET deleted_at = now()
                        WHERE team_id = :teamId
                          AND user_id = :userId
                          AND agent_id = :agentId
                          AND deleted_at IS NULL
                          AND (id = ANY(:messageIds) OR session_id = ANY(:sessionIds))
                        RETURNING id
                        """)
                .bind("teamId", teamId)
                .bind("userId", userId)
                .bind("agentId", agentId)
                .bind("messageIds", messageIds.toArray(Long[]::new))
                .bind("sessionIds", sessionIds.toArray(String[]::new))
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .count();
    }

    public Mono<Long> deleteMessagesByScope(String teamId, String userId, String agentId) {
        return databaseClient.sql("""
                        UPDATE ai_message
                        SET deleted_at = now()
                        WHERE team_id = :teamId
                          AND user_id = :userId
                          AND agent_id = :agentId
                          AND deleted_at IS NULL
                        RETURNING id
                        """)
                .bind("teamId", teamId)
                .bind("userId", userId)
                .bind("agentId", agentId)
                .map((row, metadata) -> row.get("id", Long.class))
                .all()
                .count();
    }

    private static Message mapMessage(Row row) {
        return new Message(
                row.get("id", Long.class),
                row.get("conversation_id", Long.class),
                row.get("space_id", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("session_id", String.class),
                row.get("task_id", String.class),
                row.get("request_id", String.class),
                row.get("turn_index", Integer.class),
                row.get("message_index", Integer.class),
                row.get("role", String.class),
                row.get("content", String.class),
                row.get("content_type", String.class),
                row.get("token_count", Integer.class),
                row.get("model_code", String.class),
                row.get("created_at", Instant.class));
    }

    public Mono<Boolean> scopeExists(String teamId, String userId, String agentId) {
        return databaseClient.sql("""
                        SELECT (
                            EXISTS (SELECT 1 FROM ai_message
                                    WHERE team_id = :teamId AND user_id = :userId
                                      AND agent_id = :agentId AND deleted_at IS NULL)
                            OR EXISTS (SELECT 1 FROM ai_memory_l1
                                       WHERE team_id = :teamId AND user_id = :userId
                                         AND agent_id = :agentId AND status = 'ACTIVE')
                        ) AS present
                        """)
                .bind("teamId", teamId)
                .bind("userId", userId)
                .bind("agentId", agentId)
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("present", Boolean.class)))
                .one()
                .defaultIfEmpty(false);
    }

    public record ScopeRow(String teamId, String userId, String agentId) {}

    public record AgentScopeRow(String teamId, String agentId) {}
}