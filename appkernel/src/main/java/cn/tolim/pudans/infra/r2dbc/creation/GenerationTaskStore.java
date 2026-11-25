package cn.tolim.pudans.infra.r2dbc.creation;

import cn.tolim.pudans.contract.creation.GenerationTask;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import io.r2dbc.spi.Row;
import io.r2dbc.spi.RowMetadata;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;
import java.util.function.BiFunction;

@Repository
public class GenerationTaskStore {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final TypeReference<Map<String, Object>> SETTINGS_TYPE = new TypeReference<>() {};

    private static final TypeReference<List<String>> IDS_TYPE = new TypeReference<>() {};

    private static final String TASK_COLUMNS = """
            id, owner_id, workspace_id, session_id, run_id, parent_task_id, parent_asset_id,
            media_type, operation, model_id, prompt,
            settings_jsonb::text AS settings_json,
            input_asset_ids_jsonb::text AS input_asset_ids_json,
            status, provider_request_sent, provider_task_id,
            result_asset_ids_jsonb::text AS result_asset_ids_json,
            error_code, error_message, quota_reservation_id, idempotency_key,
            created_at, started_at, finished_at
            """;

    private static final String ATTEMPT_COLUMNS = """
            id, task_id, attempt_no, channel_id, model_id, provider_task_id, provider_request_id,
            status, error_code, error_message, started_at, finished_at
            """;

    private static final String INSERT_TASK = """
            INSERT INTO generation_tasks (
                id, owner_id, workspace_id, session_id, run_id, parent_task_id, parent_asset_id,
                media_type, operation, model_id, prompt, settings_jsonb, input_asset_ids_jsonb,
                status, provider_request_sent, provider_task_id, result_asset_ids_jsonb,
                error_code, error_message, quota_reservation_id, idempotency_key,
                created_at, started_at, finished_at)
            VALUES (
                :id, :ownerId, :workspaceId, :sessionId, :runId, :parentTaskId, :parentAssetId,
                :mediaType, :operation, :modelId, :prompt, CAST(:settingsJson AS jsonb),
                CAST(:inputAssetIdsJson AS jsonb), :status, :providerRequestSent, :providerTaskId,
                CAST(:resultAssetIdsJson AS jsonb), :errorCode, :errorMessage, :quotaReservationId,
                :idempotencyKey, :createdAt, :startedAt, :finishedAt)
            ON CONFLICT (idempotency_key) DO NOTHING
            RETURNING
            """ + TASK_COLUMNS;

    private static final String SELECT_TASK =
            "SELECT " + TASK_COLUMNS + " FROM generation_tasks ";

    private static final String INSERT_ATTEMPT = """
            INSERT INTO generation_attempts (
                id, task_id, attempt_no, channel_id, model_id, provider_task_id, provider_request_id,
                status, error_code, error_message, started_at, finished_at)
            VALUES (
                :id, :taskId, :attemptNo, :channelId, :modelId, :providerTaskId, :providerRequestId,
                :status, :errorCode, :errorMessage, :startedAt, :finishedAt)
            RETURNING
            """ + ATTEMPT_COLUMNS;

    private final DatabaseClient databaseClient;

    public GenerationTaskStore(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    public record Insertion(GenerationTask task, boolean created) {}

    public record Processing(GenerationTask task, GenerationTask.Attempt attempt) {}

    // 唯一键保证重复提交只落一行，唯一键已按归属命名空间散列。
    @Transactional
    public Mono<Insertion> insertIfAbsent(GenerationTask task) {
        return bindTask(databaseClient.sql(INSERT_TASK), task)
                .map(taskMapper())
                .one()
                .switchIfEmpty(Mono.defer(() -> findByIdempotencyKey(task.idempotencyKey())
                        .switchIfEmpty(Mono.error(new IllegalStateException(
                                "generation task idempotency conflict without existing row")))))
                .map(found -> new Insertion(found, found.id().equals(task.id())));
    }

    public Mono<GenerationTask> findById(UUID id) {
        return databaseClient
                .sql(SELECT_TASK + "WHERE id = :id")
                .bind("id", id)
                .map(taskMapper())
                .one();
    }

    public Flux<GenerationTask> listMine(
            String ownerId, String workspaceId, Instant before, int limit) {
        var spec = databaseClient
                .sql(SELECT_TASK
                        + """
                          WHERE owner_id = :ownerId AND workspace_id = :workspaceId
                            AND (:before IS NULL OR created_at < CAST(:before AS timestamptz))
                          ORDER BY created_at DESC, id DESC
                          LIMIT :limit
                          """)
                .bind("ownerId", ownerId)
                .bind("workspaceId", workspaceId)
                .bind("limit", limit);
        spec = before == null
                ? spec.bindNull("before", OffsetDateTime.class)
                : spec.bind("before", OffsetDateTime.ofInstant(before, ZoneOffset.UTC));
        return spec.map(taskMapper()).all();
    }

    // 恢复扫描只读取可能卡住的非终态任务。
    public Flux<GenerationTask> listRecoverable(Instant olderThan, int limit) {
        return databaseClient
                .sql(SELECT_TASK
                        + """
                          WHERE status IN ('queued', 'retry_queued', 'dispatching',
                                           'provider_running', 'downloading', 'archiving')
                            AND COALESCE(started_at, created_at) < CAST(:olderThan AS timestamptz)
                          ORDER BY created_at
                          LIMIT :limit
                          """)
                .bind("olderThan", OffsetDateTime.ofInstant(olderThan, ZoneOffset.UTC))
                .bind("limit", limit)
                .map(taskMapper())
                .all();
    }

    @Transactional
    public Mono<Processing> beginProcessing(
            UUID taskId,
            Collection<GenerationTask.Status> expected,
            Collection<GenerationTask.Status> dispatchable,
            GenerationTask.Attempt attempt) {
        var expectedSql = placeholders(expected, "expected");
        var dispatchSql = placeholders(dispatchable, "dispatch");
        var sql = """
                UPDATE generation_tasks
                SET status = CASE WHEN status IN (%s) THEN :dispatching ELSE status END,
                    started_at = COALESCE(started_at, :now),
                    error_code = NULL,
                    error_message = NULL,
                    finished_at = NULL
                WHERE id = :id AND status IN (%s)
                RETURNING
                """.formatted(dispatchSql, expectedSql) + TASK_COLUMNS;
        var spec = databaseClient
                .sql(sql)
                .bind("id", taskId)
                .bind("dispatching", GenerationTask.Status.DISPATCHING.getCode())
                .bind("now", offset(Instant.now()));
        spec = bindStatuses(spec, expected, "expected");
        spec = bindStatuses(spec, dispatchable, "dispatch");
        return spec.map(taskMapper())
                .one()
                .flatMap(updated -> insertAttempt(attempt).thenReturn(new Processing(updated, attempt)));
    }

    public Mono<GenerationTask> markProviderRequestStarted(UUID taskId) {
        return databaseClient
                .sql("""
                        UPDATE generation_tasks
                        SET provider_request_sent = TRUE
                        WHERE id = :id AND status = 'dispatching' AND provider_request_sent = FALSE
                        RETURNING
                        """ + TASK_COLUMNS)
                .bind("id", taskId)
                .map(taskMapper())
                .one();
    }

    public Mono<GenerationTask> confirmProviderRunning(UUID taskId, String providerTaskId) {
        var spec = databaseClient
                .sql("""
                        UPDATE generation_tasks
                        SET status = 'provider_running',
                            provider_task_id = :providerTaskId,
                            error_code = NULL,
                            error_message = NULL
                        WHERE id = :id AND status = 'dispatching'
                        RETURNING
                        """ + TASK_COLUMNS)
                .bind("id", taskId);
        spec = providerTaskId == null
                ? spec.bindNull("providerTaskId", String.class)
                : spec.bind("providerTaskId", providerTaskId);
        return spec.map(taskMapper()).one();
    }

    public Mono<GenerationTask> transition(
            UUID taskId, Collection<GenerationTask.Status> expected, GenerationTask.Status target) {
        var expectedSql = placeholders(expected, "expected");
        var sql = """
                UPDATE generation_tasks
                SET status = :target, started_at = COALESCE(started_at, :now)
                WHERE id = :id AND status IN (%s)
                RETURNING
                """.formatted(expectedSql) + TASK_COLUMNS;
        var spec = databaseClient
                .sql(sql)
                .bind("id", taskId)
                .bind("target", target.getCode())
                .bind("now", offset(Instant.now()));
        spec = bindStatuses(spec, expected, "expected");
        return spec.map(taskMapper()).one();
    }

    @Transactional
    public Mono<GenerationTask> succeed(
            UUID taskId, UUID attemptId, List<String> resultAssetIds, Instant finishedAt) {
        return databaseClient
                .sql("""
                        UPDATE generation_tasks
                        SET status = 'succeeded',
                            result_asset_ids_jsonb = CAST(:resultAssetIdsJson AS jsonb),
                            error_code = NULL,
                            error_message = NULL,
                            finished_at = :finishedAt
                        WHERE id = :id AND status = 'archiving'
                        RETURNING
                        """ + TASK_COLUMNS)
                .bind("id", taskId)
                .bind("resultAssetIdsJson", JSON.writeValueAsString(resultAssetIds))
                .bind("finishedAt", offset(finishedAt))
                .map(taskMapper())
                .one()
                .flatMap(task -> finishAttempt(
                                attemptId,
                                GenerationTask.AttemptStatus.SUCCEEDED,
                                null,
                                null,
                                null,
                                null,
                                finishedAt)
                        .thenReturn(task));
    }

    @Transactional
    public Mono<GenerationTask> fail(
            UUID taskId,
            UUID attemptId,
            Collection<GenerationTask.Status> expected,
            String errorCode,
            String errorMessage,
            boolean providerRequestSent,
            String providerTaskId,
            Instant finishedAt) {
        var expectedSql = placeholders(expected, "expected");
        var sql = """
                UPDATE generation_tasks
                SET status = 'failed',
                    provider_request_sent = :providerRequestSent,
                    provider_task_id = :providerTaskId,
                    error_code = :errorCode,
                    error_message = :errorMessage,
                    finished_at = :finishedAt
                WHERE id = :id AND status IN (%s)
                RETURNING
                """.formatted(expectedSql) + TASK_COLUMNS;
        var spec = databaseClient
                .sql(sql)
                .bind("id", taskId)
                .bind("providerRequestSent", providerRequestSent)
                .bind("errorCode", errorCode)
                .bind("errorMessage", errorMessage)
                .bind("finishedAt", offset(finishedAt));
        spec = providerTaskId == null
                ? spec.bindNull("providerTaskId", String.class)
                : spec.bind("providerTaskId", providerTaskId);
        spec = bindStatuses(spec, expected, "expected");
        return spec.map(taskMapper())
                .one()
                .flatMap(task -> {
                    if (attemptId == null) {
                        return Mono.just(task);
                    }
                    return finishAttempt(
                                    attemptId,
                                    GenerationTask.AttemptStatus.FAILED,
                                    errorCode,
                                    errorMessage,
                                    providerTaskId,
                                    null,
                                    finishedAt)
                            .thenReturn(task);
                });
    }

    @Transactional
    public Mono<GenerationTask> cancel(
            UUID taskId,
            Collection<GenerationTask.Status> expected,
            String errorCode,
            String errorMessage,
            Instant finishedAt) {
        var expectedSql = placeholders(expected, "expected");
        var sql = """
                UPDATE generation_tasks
                SET status = 'cancelled',
                    error_code = :errorCode,
                    error_message = :errorMessage,
                    finished_at = :finishedAt
                WHERE id = :id AND status IN (%s)
                RETURNING
                """.formatted(expectedSql) + TASK_COLUMNS;
        var spec = databaseClient
                .sql(sql)
                .bind("id", taskId)
                .bind("finishedAt", offset(finishedAt));
        spec = errorCode == null ? spec.bindNull("errorCode", String.class) : spec.bind("errorCode", errorCode);
        spec = errorMessage == null
                ? spec.bindNull("errorMessage", String.class)
                : spec.bind("errorMessage", errorMessage);
        spec = bindStatuses(spec, expected, "expected");
        return spec.map(taskMapper())
                .one()
                .flatMap(task -> {
                    var cancelSpec = databaseClient
                            .sql("""
                                    UPDATE generation_attempts
                                    SET status = 'cancelled',
                                        error_code = :errorCode,
                                        error_message = :errorMessage,
                                        finished_at = :finishedAt
                                    WHERE task_id = :taskId
                                      AND status IN ('requesting', 'submitted', 'running')
                                    """)
                            .bind("taskId", taskId)
                            .bind("finishedAt", offset(finishedAt));
                    cancelSpec = errorCode == null
                            ? cancelSpec.bindNull("errorCode", String.class)
                            : cancelSpec.bind("errorCode", errorCode);
                    cancelSpec = errorMessage == null
                            ? cancelSpec.bindNull("errorMessage", String.class)
                            : cancelSpec.bind("errorMessage", errorMessage);
                    return cancelSpec.then().thenReturn(task);
                });
    }

    public Mono<GenerationTask> retry(
            UUID taskId, boolean providerRequestSent, String providerTaskId) {
        var spec = databaseClient
                .sql("""
                        UPDATE generation_tasks
                        SET status = 'retry_queued',
                            provider_request_sent = :providerRequestSent,
                            provider_task_id = :providerTaskId,
                            error_code = NULL,
                            error_message = NULL,
                            finished_at = NULL
                        WHERE id = :id AND status = 'failed'
                        RETURNING
                        """ + TASK_COLUMNS)
                .bind("id", taskId)
                .bind("providerRequestSent", providerRequestSent);
        spec = providerTaskId == null
                ? spec.bindNull("providerTaskId", String.class)
                : spec.bind("providerTaskId", providerTaskId);
        return spec.map(taskMapper()).one();
    }

    private Mono<GenerationTask.Attempt> insertAttempt(GenerationTask.Attempt attempt) {
        return bindAttempt(databaseClient.sql(INSERT_ATTEMPT), attempt)
                .map(attemptMapper())
                .one();
    }

    public Mono<GenerationTask.Attempt> markAttemptSubmitted(
            UUID attemptId,
            String providerTaskId,
            String providerRequestId,
            UUID channelId) {
        var spec = databaseClient
                .sql("""
                        UPDATE generation_attempts
                        SET status = 'submitted',
                            provider_task_id = :providerTaskId,
                            provider_request_id = :providerRequestId,
                            channel_id = COALESCE(:channelId, channel_id)
                        WHERE id = :id AND status = 'requesting'
                        RETURNING
                        """ + ATTEMPT_COLUMNS)
                .bind("id", attemptId);
        spec = providerTaskId == null
                ? spec.bindNull("providerTaskId", String.class)
                : spec.bind("providerTaskId", providerTaskId);
        spec = providerRequestId == null
                ? spec.bindNull("providerRequestId", String.class)
                : spec.bind("providerRequestId", providerRequestId);
        spec = channelId == null ? spec.bindNull("channelId", UUID.class) : spec.bind("channelId", channelId);
        return spec.map(attemptMapper()).one();
    }

    public Mono<GenerationTask.Attempt> markAttemptRunning(UUID attemptId) {
        return databaseClient
                .sql("""
                        UPDATE generation_attempts
                        SET status = 'running'
                        WHERE id = :id AND status IN ('requesting', 'submitted')
                        RETURNING
                        """ + ATTEMPT_COLUMNS)
                .bind("id", attemptId)
                .map(attemptMapper())
                .one();
    }

    public Mono<GenerationTask.Attempt> markAttemptCancelled(UUID attemptId, Instant finishedAt) {
        return finishAttempt(
                attemptId,
                GenerationTask.AttemptStatus.CANCELLED,
                null,
                null,
                null,
                null,
                finishedAt);
    }

    private Mono<GenerationTask.Attempt> finishAttempt(
            UUID attemptId,
            GenerationTask.AttemptStatus status,
            String errorCode,
            String errorMessage,
            String providerTaskId,
            String providerRequestId,
            Instant finishedAt) {
        var spec = databaseClient
                .sql("""
                        UPDATE generation_attempts
                        SET status = :status,
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            provider_task_id = COALESCE(:providerTaskId, provider_task_id),
                            provider_request_id = COALESCE(:providerRequestId, provider_request_id),
                            finished_at = :finishedAt
                        WHERE id = :id
                        RETURNING
                        """ + ATTEMPT_COLUMNS)
                .bind("id", attemptId)
                .bind("status", status.getCode())
                .bind("finishedAt", offset(finishedAt));
        spec = errorCode == null ? spec.bindNull("errorCode", String.class) : spec.bind("errorCode", errorCode);
        spec = errorMessage == null
                ? spec.bindNull("errorMessage", String.class)
                : spec.bind("errorMessage", errorMessage);
        spec = providerTaskId == null
                ? spec.bindNull("providerTaskId", String.class)
                : spec.bind("providerTaskId", providerTaskId);
        spec = providerRequestId == null
                ? spec.bindNull("providerRequestId", String.class)
                : spec.bind("providerRequestId", providerRequestId);
        return spec.map(attemptMapper()).one();
    }

    public Flux<GenerationTask.Attempt> listAttempts(UUID taskId) {
        return databaseClient
                .sql("SELECT " + ATTEMPT_COLUMNS
                        + " FROM generation_attempts WHERE task_id = :taskId ORDER BY attempt_no")
                .bind("taskId", taskId)
                .map(attemptMapper())
                .all();
    }

    public Mono<Long> countAttempts(UUID taskId) {
        return databaseClient
                .sql("SELECT COUNT(*) AS attempt_count FROM generation_attempts WHERE task_id = :taskId")
                .bind("taskId", taskId)
                .map((row, metadata) -> row.get("attempt_count", Long.class))
                .one()
                .defaultIfEmpty(0L);
    }

    public Mono<GenerationTask> findByIdempotencyKey(String idempotencyKey) {
        return databaseClient
                .sql(SELECT_TASK + "WHERE idempotency_key = :idempotencyKey")
                .bind("idempotencyKey", idempotencyKey)
                .map(taskMapper())
                .one();
    }

    private static DatabaseClient.GenericExecuteSpec bindTask(
            DatabaseClient.GenericExecuteSpec spec, GenerationTask task) {
        spec = spec.bind("id", task.id())
                .bind("ownerId", task.ownerId())
                .bind("workspaceId", task.workspaceId())
                .bind("mediaType", task.mediaType().getCode())
                .bind("operation", task.operation().getCode())
                .bind("modelId", task.modelId())
                .bind("settingsJson", JSON.writeValueAsString(task.settings()))
                .bind("inputAssetIdsJson", JSON.writeValueAsString(task.inputAssetIds()))
                .bind("status", task.status().getCode())
                .bind("providerRequestSent", task.providerRequestSent())
                .bind("resultAssetIdsJson", JSON.writeValueAsString(task.resultAssetIds()))
                .bind("idempotencyKey", task.idempotencyKey())
                .bind("createdAt", offset(task.createdAt()));
        spec = nullable(spec, "sessionId", task.sessionId(), UUID.class);
        spec = nullable(spec, "runId", task.runId(), UUID.class);
        spec = nullable(spec, "parentTaskId", task.parentTaskId(), UUID.class);
        spec = nullable(spec, "parentAssetId", task.parentAssetId(), UUID.class);
        spec = nullable(spec, "prompt", task.prompt(), String.class);
        spec = nullable(spec, "providerTaskId", task.providerTaskId(), String.class);
        spec = nullable(spec, "errorCode", task.errorCode(), String.class);
        spec = nullable(spec, "errorMessage", task.errorMessage(), String.class);
        spec = nullable(spec, "quotaReservationId", task.quotaReservationId(), UUID.class);
        spec = nullable(spec, "startedAt", task.startedAt(), OffsetDateTime.class);
        return nullable(spec, "finishedAt", task.finishedAt(), OffsetDateTime.class);
    }

    private static DatabaseClient.GenericExecuteSpec bindAttempt(
            DatabaseClient.GenericExecuteSpec spec, GenerationTask.Attempt attempt) {
        spec = spec.bind("id", attempt.id())
                .bind("taskId", attempt.taskId())
                .bind("attemptNo", attempt.attemptNo())
                .bind("status", attempt.status().getCode());
        spec = nullable(spec, "channelId", attempt.channelId(), UUID.class);
        spec = nullable(spec, "modelId", attempt.modelId(), UUID.class);
        spec = nullable(spec, "providerTaskId", attempt.providerTaskId(), String.class);
        spec = nullable(spec, "providerRequestId", attempt.providerRequestId(), String.class);
        spec = nullable(spec, "errorCode", attempt.errorCode(), String.class);
        spec = nullable(spec, "errorMessage", attempt.errorMessage(), String.class);
        spec = nullable(spec, "startedAt", attempt.startedAt(), OffsetDateTime.class);
        return nullable(spec, "finishedAt", attempt.finishedAt(), OffsetDateTime.class);
    }

    private static DatabaseClient.GenericExecuteSpec nullable(
            DatabaseClient.GenericExecuteSpec spec, String name, Object value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, value);
    }

    private static DatabaseClient.GenericExecuteSpec nullable(
            DatabaseClient.GenericExecuteSpec spec, String name, Instant value, Class<?> type) {
        return value == null ? spec.bindNull(name, type) : spec.bind(name, offset(value));
    }

    private static DatabaseClient.GenericExecuteSpec bindStatuses(
            DatabaseClient.GenericExecuteSpec spec,
            Collection<GenerationTask.Status> statuses,
            String prefix) {
        var index = 0;
        for (var status : statuses) {
            spec = spec.bind(prefix + index, status.getCode());
            index++;
        }
        return spec;
    }

    private static String placeholders(Collection<?> values, String prefix) {
        return IntStream.range(0, values.size())
                .mapToObj(index -> ":" + prefix + index)
                .collect(Collectors.joining(","));
    }

    private static OffsetDateTime offset(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private static BiFunction<Row, RowMetadata, GenerationTask> taskMapper() {
        return (row, metadata) -> new GenerationTask(
                row.get("id", UUID.class),
                row.get("owner_id", String.class),
                row.get("workspace_id", String.class),
                row.get("session_id", UUID.class),
                row.get("run_id", UUID.class),
                row.get("parent_task_id", UUID.class),
                row.get("parent_asset_id", UUID.class),
                GenerationTask.MediaType.fromCode(row.get("media_type", String.class)),
                GenerationTask.Operation.fromCode(row.get("operation", String.class)),
                row.get("model_id", UUID.class),
                row.get("prompt", String.class),
                jsonSettings(row.get("settings_json", String.class)),
                jsonIds(row.get("input_asset_ids_json", String.class)),
                GenerationTask.Status.fromCode(row.get("status", String.class)),
                Boolean.TRUE.equals(row.get("provider_request_sent", Boolean.class)),
                row.get("provider_task_id", String.class),
                jsonIds(row.get("result_asset_ids_json", String.class)),
                row.get("error_code", String.class),
                row.get("error_message", String.class),
                row.get("quota_reservation_id", UUID.class),
                row.get("idempotency_key", String.class),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("started_at", OffsetDateTime.class)),
                instant(row.get("finished_at", OffsetDateTime.class)));
    }

    private static BiFunction<Row, RowMetadata, GenerationTask.Attempt> attemptMapper() {
        return (row, metadata) -> new GenerationTask.Attempt(
                row.get("id", UUID.class),
                row.get("task_id", UUID.class),
                row.get("attempt_no", Integer.class),
                row.get("channel_id", UUID.class),
                row.get("model_id", UUID.class),
                row.get("provider_task_id", String.class),
                row.get("provider_request_id", String.class),
                GenerationTask.AttemptStatus.fromCode(row.get("status", String.class)),
                row.get("error_code", String.class),
                row.get("error_message", String.class),
                instant(row.get("started_at", OffsetDateTime.class)),
                instant(row.get("finished_at", OffsetDateTime.class)));
    }

    private static Map<String, Object> jsonSettings(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        var parsed = JSON.readValue(json, SETTINGS_TYPE);
        return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
    }

    private static List<String> jsonIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        var parsed = JSON.readValue(json, IDS_TYPE);
        return parsed == null ? List.of() : List.copyOf(parsed);
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
