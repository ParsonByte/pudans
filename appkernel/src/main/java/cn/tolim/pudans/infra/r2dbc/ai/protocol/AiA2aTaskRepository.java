package cn.tolim.pudans.infra.r2dbc.ai.protocol;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.protocol.a2a.A2aTaskRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiA2aTaskRepository {

    private final DatabaseClient databaseClient;

    public Mono<Void> upsert(A2aTaskRecord record) {
        var artifact = record.artifact() == null ? "null" : JsonUtils.objectToJson(record.artifact());
        return databaseClient.sql("""
                        INSERT INTO ai_a2a_task (
                            user_id, task_id, context_id, status, artifact, updated_at
                        ) VALUES (
                            :userId, :taskId, :contextId, :status, CAST(:artifact AS jsonb), now()
                        )
                        ON CONFLICT (user_id, task_id) DO UPDATE
                        SET context_id = EXCLUDED.context_id,
                            status = EXCLUDED.status,
                            artifact = EXCLUDED.artifact,
                            updated_at = now()
                        """)
                .bind("userId", record.userId())
                .bind("taskId", record.taskId())
                .bind("contextId", record.contextId())
                .bind("status", record.status().state())
                .bind("artifact", artifact)
                .then();
    }

    public Mono<TaskRow> find(String userId, String taskId) {
        return databaseClient.sql("""
                        SELECT context_id, status, artifact::text AS artifact
                        FROM ai_a2a_task
                        WHERE user_id = :userId AND task_id = :taskId
                        """)
                .bind("userId", userId)
                .bind("taskId", taskId)
                .map((row, metadata) -> new TaskRow(
                        row.get("context_id", String.class),
                        row.get("status", String.class),
                        row.get("artifact", String.class)))
                .one();
    }

    public record TaskRow(String contextId, String status, String artifactJson) {}
}