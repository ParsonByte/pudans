package cn.tolim.pudans.infra.r2dbc.ai.task;

import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.task.AiTask;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import com.fasterxml.jackson.core.type.TypeReference;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiTaskRepository {

    public static final String DEFAULT_SPACE = "default";

    private static final int STATUS_PENDING = 0;

    private static final int STATUS_RUNNING = 1;

    private static final int STATUS_DONE = 2;

    private static final int STATUS_FAILED = 3;

    private static final int DEFAULT_MAX_RETRY = 3;

    private final DatabaseClient databaseClient;

    public Mono<Boolean> enqueue(NewTask task) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_task (
                            task_type, dedup_key, space_id, user_id, team_id, agent_id,
                            session_id, conversation_id, priority, payload, next_run_at, max_retry
                        ) VALUES (
                            :taskType, :dedupKey, :spaceId, :userId, :teamId, :agentId,
                            :sessionId, :conversationId, :priority, CAST(:payload AS jsonb),
                            COALESCE(:nextRunAt, now()), :maxRetry
                        )
                        ON CONFLICT DO NOTHING
                        RETURNING id
                        """)
                .bind("taskType", task.taskType())
                .bind("spaceId", task.spaceId())
                .bind("priority", task.priority())
                .bind("payload", JsonUtils.objectToJson(task.payload()))
                .bind("maxRetry", task.maxRetry());
        spec = AiR2dbcBindings.bindNullable(spec, "dedupKey", task.dedupKey(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "userId", task.userId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", task.teamId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agentId", task.agentId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "sessionId", task.sessionId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "conversationId", task.conversationId(), Long.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "nextRunAt", offset(task.nextRunAt()), OffsetDateTime.class);
        return spec.map((row, metadata) -> Boolean.TRUE).one().defaultIfEmpty(false);
    }

    public Flux<AiTask> claim(String workerId, int batchSize, int lockSeconds) {
        return databaseClient.sql("""
                        WITH picked AS (
                            SELECT id FROM ai_task
                            WHERE status = 0 AND next_run_at <= now()
                            ORDER BY priority DESC, next_run_at, id
                            FOR UPDATE SKIP LOCKED
                            LIMIT :batchSize
                        )
                        UPDATE ai_task task
                        SET status = 1,
                            locked_by = :workerId,
                            locked_until = now() + make_interval(secs => :lockSeconds),
                            updated_at = now()
                        FROM picked
                        WHERE task.id = picked.id
                        RETURNING task.*
                        """)
                .bind("workerId", workerId)
                .bind("batchSize", batchSize)
                .bind("lockSeconds", lockSeconds)
                .map((row, metadata) -> mapTask(row))
                .all();
    }

    public Mono<Boolean> markDone(long id) {
        return databaseClient.sql("""
                        UPDATE ai_task
                        SET status = :status,
                            locked_by = NULL,
                            locked_until = NULL,
                            updated_at = now()
                        WHERE id = :id AND status = :running
                        """)
                .bind("status", STATUS_DONE)
                .bind("running", STATUS_RUNNING)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    public Mono<Boolean> markFailed(long id, String error, Instant nextRunAt) {
        return databaseClient.sql("""
                        UPDATE ai_task
                        SET retry_count = retry_count + 1,
                            status = CASE WHEN retry_count + 1 >= max_retry THEN :failed ELSE :pending END,
                            next_run_at = :nextRunAt,
                            last_error = :error,
                            locked_by = NULL,
                            locked_until = NULL,
                            updated_at = now()
                        WHERE id = :id AND status = :running
                        """)
                .bind("failed", STATUS_FAILED)
                .bind("pending", STATUS_PENDING)
                .bind("running", STATUS_RUNNING)
                .bind("nextRunAt", offset(nextRunAt))
                .bind("error", truncate(error, 1024))
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    public Mono<Long> releaseExpired() {
        return databaseClient.sql("""
                        UPDATE ai_task
                        SET status = :pending,
                            locked_by = NULL,
                            locked_until = NULL,
                            updated_at = now()
                        WHERE status = :running AND locked_until < now()
                        """)
                .bind("pending", STATUS_PENDING)
                .bind("running", STATUS_RUNNING)
                .fetch()
                .rowsUpdated();
    }

    static AiTask mapTask(Row row) {
        return new AiTask(
                row.get("id", Long.class),
                row.get("task_type", String.class),
                row.get("dedup_key", String.class),
                row.get("space_id", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("session_id", String.class),
                row.get("conversation_id", Long.class),
                row.get("priority", Integer.class),
                payload(row),
                row.get("retry_count", Integer.class),
                row.get("max_retry", Integer.class),
                instant(row.get("next_run_at", OffsetDateTime.class)),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }

    private static Map<String, Object> payload(Row row) {
        var value = row.get("payload", Object.class);
        if (value == null) {
            return Map.of();
        }
        var text = value.toString();
        if (text.isBlank()) {
            return Map.of();
        }
        return JsonUtils.jsonToObject(text, new TypeReference<Map<String, Object>>() {});
    }

    private static Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    private static OffsetDateTime offset(@Nullable Instant value) {
        return value == null ? null : OffsetDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record NewTask(
            String taskType,
            @Nullable String dedupKey,
            String spaceId,
            @Nullable String userId,
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String sessionId,
            @Nullable Long conversationId,
            int priority,
            Map<String, Object> payload,
            @Nullable Instant nextRunAt,
            int maxRetry) {

        public NewTask {
            if (taskType == null || taskType.isBlank()) {
                throw new IllegalArgumentException("taskType 不能为空");
            }
            if (spaceId == null || spaceId.isBlank()) {
                throw new IllegalArgumentException("spaceId 不能为空");
            }
            if (priority < 0) {
                throw new IllegalArgumentException("priority 不能为负数");
            }
            payload = payload == null ? Map.of() : Map.copyOf(payload);
            maxRetry = maxRetry <= 0 ? DEFAULT_MAX_RETRY : maxRetry;
        }
    }
}
