package cn.tolim.pudans.core.infra.r2dbc.ai.agent;

import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiAgentStateRepository {

    private final DatabaseClient databaseClient;

    public Mono<StateRow> find(String userId, String sessionId) {
        return databaseClient.sql("""
                        SELECT state::text AS state, version
                        FROM ai_agent_state
                        WHERE user_id = :userId AND session_id = :sessionId
                        """)
                .bind("userId", userId)
                .bind("sessionId", sessionId)
                .map((row, metadata) -> new StateRow(
                        row.get("state", String.class),
                        row.get("version", Long.class)))
                .one();
    }

    public Mono<Long> save(String userId, String sessionId, String state) {
        return databaseClient.sql("""
                        INSERT INTO ai_agent_state (user_id, session_id, state, version, updated_at)
                        VALUES (:userId, :sessionId, CAST(:state AS jsonb), 1, now())
                        ON CONFLICT (user_id, session_id) DO UPDATE
                        SET state = EXCLUDED.state,
                            version = ai_agent_state.version + 1,
                            updated_at = now()
                        RETURNING version
                        """)
                .bind("userId", userId)
                .bind("sessionId", sessionId)
                .bind("state", state)
                .map((row, metadata) -> row.get("version", Long.class))
                .one();
    }

    public Mono<Long> saveIfVersion(
            String userId, String sessionId, String state, long expectedVersion) {
        return databaseClient.sql("""
                        UPDATE ai_agent_state
                        SET state = CAST(:state AS jsonb),
                            version = version + 1,
                            updated_at = now()
                        WHERE user_id = :userId
                          AND session_id = :sessionId
                          AND version = :version
                        RETURNING version
                        """)
                .bind("userId", userId)
                .bind("sessionId", sessionId)
                .bind("state", state)
                .bind("version", expectedVersion)
                .map((row, metadata) -> row.get("version", Long.class))
                .one();
    }

    public Mono<Void> delete(String userId, String sessionId) {
        return databaseClient.sql("""
                        DELETE FROM ai_agent_state
                        WHERE user_id = :userId AND session_id = :sessionId
                        """)
                .bind("userId", userId)
                .bind("sessionId", sessionId)
                .then();
    }

    public record StateRow(String state, Long version) {}
}