package cn.tolim.pudans.core.infra.r2dbc.ai.gateway;

import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiR2dbcBindings;
import io.r2dbc.spi.Row;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiGatewaySessionRepository {

    private final DatabaseClient databaseClient;

    public Mono<GatewaySession> upsert(NewGatewaySession newSession) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_gateway_session (
                            space_id, user_id, agent_source, session_key, session_id,
                            team_id, agent_id, task_id, conversation_id, status
                        ) VALUES (
                            :spaceId, :userId, :agentSource, :sessionKey, :sessionId,
                            :teamId, :agentId, :taskId, :conversationId, 'ACTIVE'
                        )
                        ON CONFLICT (space_id, user_id, agent_source, session_key) DO UPDATE
                        SET session_id = COALESCE(EXCLUDED.session_id, ai_gateway_session.session_id),
                            team_id = EXCLUDED.team_id,
                            agent_id = EXCLUDED.agent_id,
                            task_id = COALESCE(EXCLUDED.task_id, ai_gateway_session.task_id),
                            conversation_id = COALESCE(
                                EXCLUDED.conversation_id, ai_gateway_session.conversation_id),
                            status = 'ACTIVE',
                            updated_at = now()
                        RETURNING id, space_id, user_id, agent_source, session_key, session_id,
                                  team_id, agent_id, task_id, conversation_id, status, created_at, updated_at
                        """)
                .bind("spaceId", newSession.spaceId())
                .bind("userId", newSession.userId())
                .bind("agentSource", newSession.agentSource())
                .bind("sessionKey", newSession.sessionKey())
                .bind("teamId", newSession.teamId())
                .bind("agentId", newSession.agentId());
        spec = AiR2dbcBindings.bindNullable(spec, "sessionId", newSession.sessionId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "taskId", newSession.taskId(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "conversationId", newSession.conversationId(), Long.class);
        return spec.map((row, metadata) -> mapGatewaySession(row)).one();
    }

    public Mono<GatewaySession> findBySession(
            String spaceId, String userId, String agentSource, String sessionKey) {
        return databaseClient.sql("""
                        SELECT id, space_id, user_id, agent_source, session_key, session_id,
                               team_id, agent_id, task_id, conversation_id, status, created_at, updated_at
                        FROM ai_gateway_session
                        WHERE space_id = :spaceId
                          AND user_id = :userId
                          AND agent_source = :agentSource
                          AND session_key = :sessionKey
                        """)
                .bind("spaceId", spaceId)
                .bind("userId", userId)
                .bind("agentSource", agentSource)
                .bind("sessionKey", sessionKey)
                .map((row, metadata) -> mapGatewaySession(row))
                .one();
    }

    public Mono<Boolean> deleteBySession(
            String spaceId, String userId, String agentSource, String sessionKey) {        return databaseClient.sql("""
                        DELETE FROM ai_gateway_session
                        WHERE space_id = :spaceId
                          AND user_id = :userId
                          AND agent_source = :agentSource
                          AND session_key = :sessionKey
                        RETURNING id
                        """)
                .bind("spaceId", spaceId)
                .bind("userId", userId)
                .bind("agentSource", agentSource)
                .bind("sessionKey", sessionKey)
                .map((row, metadata) -> Boolean.TRUE)
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<GatewaySession> findById(long id) {
        return databaseClient.sql("""
                        SELECT id, space_id, user_id, agent_source, session_key, session_id,
                               team_id, agent_id, task_id, conversation_id, status, created_at, updated_at
                        FROM ai_gateway_session
                        WHERE id = :id
                        """)
                .bind("id", id)
                .map((row, metadata) -> mapGatewaySession(row))
                .one();
    }

    public Flux<GatewaySession> list(long beforeId, int limit) {
        return databaseClient.sql("""
                        SELECT id, space_id, user_id, agent_source, session_key, session_id,
                               team_id, agent_id, task_id, conversation_id, status, created_at, updated_at
                        FROM ai_gateway_session
                        WHERE id < :beforeId
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> mapGatewaySession(row))
                .all();
    }

    public Mono<Boolean> deleteById(long id) {
        return databaseClient.sql("""
                        DELETE FROM ai_gateway_session
                        WHERE id = :id
                        RETURNING id
                        """)
                .bind("id", id)
                .map((row, metadata) -> Boolean.TRUE)
                .one()
                .defaultIfEmpty(false);
    }

    static GatewaySession mapGatewaySession(Row row) {
        return new GatewaySession(
                row.get("id", Long.class),
                row.get("space_id", String.class),
                row.get("user_id", String.class),
                row.get("agent_source", String.class),
                row.get("session_key", String.class),
                row.get("session_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("task_id", String.class),
                row.get("conversation_id", Long.class),
                row.get("status", String.class),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class));
    }

    public record NewGatewaySession(
            String spaceId,
            String userId,
            String agentSource,
            String sessionKey,
            @Nullable String sessionId,
            String teamId,
            String agentId,
            @Nullable String taskId,
            @Nullable Long conversationId) {

        public NewGatewaySession {
            requireText(spaceId, "spaceId");
            requireText(userId, "userId");
            requireText(agentSource, "agentSource");
            requireText(sessionKey, "sessionKey");
            requireText(teamId, "teamId");
            requireText(agentId, "agentId");
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }

    public record GatewaySession(
            long id,
            String spaceId,
            String userId,
            String agentSource,
            String sessionKey,
            @Nullable String sessionId,
            String teamId,
            String agentId,
            @Nullable String taskId,
            @Nullable Long conversationId,
            String status,
            Instant createdAt,
            Instant updatedAt) {}
}
