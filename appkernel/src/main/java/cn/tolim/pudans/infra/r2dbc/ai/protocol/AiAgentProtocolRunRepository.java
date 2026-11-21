package cn.tolim.pudans.infra.r2dbc.ai.protocol;

import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiAgentProtocolRunRepository {

    private final DatabaseClient databaseClient;

    public Mono<Void> upsert(
            String userId,
            String runId,
            String threadId,
            @Nullable String agentId,
            String status,
            @Nullable String content,
            @Nullable String error) {
        return databaseClient.sql("""
                        INSERT INTO ai_agent_protocol_run (
                            user_id, run_id, thread_id, agent_id, status, content, error, updated_at
                        ) VALUES (
                            :userId, :runId, :threadId, :agentId, :status, :content, :error, now()
                        )
                        ON CONFLICT (user_id, run_id) DO UPDATE
                        SET thread_id = EXCLUDED.thread_id,
                            agent_id = EXCLUDED.agent_id,
                            status = EXCLUDED.status,
                            content = EXCLUDED.content,
                            error = EXCLUDED.error,
                            updated_at = now()
                        """)
                .bind("userId", userId)
                .bind("runId", runId)
                .bind("threadId", threadId)
                .bind("agentId", agentId == null ? "" : agentId)
                .bind("status", status)
                .bind("content", content == null ? "" : content)
                .bind("error", error == null ? "" : error)
                .then();
    }

    public Mono<RunRow> find(String userId, String runId) {
        return databaseClient.sql("""
                        SELECT user_id, run_id, thread_id, agent_id, status, content, error
                        FROM ai_agent_protocol_run
                        WHERE user_id = :userId AND run_id = :runId
                        """)
                .bind("userId", userId)
                .bind("runId", runId)
                .map((row, metadata) -> row(row))
                .one();
    }

    public Mono<RunRow> latestByThread(String userId, String threadId) {
        return databaseClient.sql("""
                        SELECT user_id, run_id, thread_id, agent_id, status, content, error
                        FROM ai_agent_protocol_run
                        WHERE user_id = :userId AND thread_id = :threadId
                        ORDER BY updated_at DESC
                        LIMIT 1
                        """)
                .bind("userId", userId)
                .bind("threadId", threadId)
                .map((row, metadata) -> row(row))
                .one();
    }

    private static RunRow row(io.r2dbc.spi.Row row) {
        return new RunRow(
                row.get("user_id", String.class),
                row.get("run_id", String.class),
                row.get("thread_id", String.class),
                row.get("agent_id", String.class),
                row.get("status", String.class),
                row.get("content", String.class),
                row.get("error", String.class));
    }

    public record RunRow(
            String userId,
            String runId,
            String threadId,
            @Nullable String agentId,
            String status,
            @Nullable String content,
            @Nullable String error) {}
}