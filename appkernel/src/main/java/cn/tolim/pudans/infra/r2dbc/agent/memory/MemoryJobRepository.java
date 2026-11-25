package cn.tolim.pudans.infra.r2dbc.agent.memory;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class MemoryJobRepository {

    private static final String COLUMNS = """
            SELECT id, job_type, scope_type, scope_id, watermark_from, watermark_to, status,
                   total_items, processed_items, failed_items, idempotency_key, created_at,
                   started_at, finished_at, error_code, error_message
              FROM memory_jobs
            """;

    private final DatabaseClient databaseClient;

    public Mono<MemoryJobEntity> insert(MemoryJobEntity entity) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO memory_jobs (
                            id, job_type, scope_type, scope_id, watermark_from, watermark_to,
                            status, total_items, processed_items, failed_items, idempotency_key,
                            created_at, started_at, finished_at, error_code, error_message)
                        VALUES (
                            :id, :jobType, :scopeType, :scopeId, :watermarkFrom, :watermarkTo,
                            :status, :totalItems, :processedItems, :failedItems, :idempotencyKey,
                            :createdAt, :startedAt, :finishedAt, :errorCode, :errorMessage)
                        """)
                .bind("id", entity.getId())
                .bind("jobType", entity.getJobType())
                .bind("scopeType", entity.getScopeType());
        spec = bindNullable(spec, "scopeId", entity.getScopeId(), String.class);
        spec = bindNullable(
                spec, "watermarkFrom", utc(entity.getWatermarkFrom()), OffsetDateTime.class);
        spec = bindNullable(
                spec, "watermarkTo", utc(entity.getWatermarkTo()), OffsetDateTime.class);
        spec = bindNullable(spec, "startedAt", utc(entity.getStartedAt()), OffsetDateTime.class);
        spec = bindNullable(spec, "finishedAt", utc(entity.getFinishedAt()), OffsetDateTime.class);
        spec = bindNullable(spec, "errorCode", entity.getErrorCode(), String.class);
        spec = bindNullable(spec, "errorMessage", entity.getErrorMessage(), String.class);
        return duplicateAs(
                spec.bind("status", entity.getStatus())
                        .bind("totalItems", entity.getTotalItems())
                        .bind("processedItems", entity.getProcessedItems())
                        .bind("failedItems", entity.getFailedItems())
                        .bind("idempotencyKey", entity.getIdempotencyKey())
                        .bind("createdAt", utc(entity.getCreatedAt()))
                        .then()
                        .thenReturn(entity),
                "memory-job-conflict",
                "记忆任务幂等键已存在。");
    }

    public Mono<MemoryJobEntity> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<MemoryJobEntity> findByIdempotencyKey(String idempotencyKey) {
        return databaseClient
                .sql(COLUMNS + " WHERE idempotency_key = :idempotencyKey")
                .bind("idempotencyKey", idempotencyKey)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Flux<MemoryJobEntity> listVisible(String ownerId, int limit) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE (scope_type = 'owner' AND scope_id = :ownerId)
                            OR scope_type = 'global'
                         ORDER BY created_at DESC
                         LIMIT :limit
                        """)
                .bind("ownerId", ownerId)
                .bind("limit", limit)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<Boolean> markRunning(UUID id, Instant startedAt) {
        return databaseClient
                .sql("""
                        UPDATE memory_jobs
                           SET status = 'running', started_at = :startedAt, error_code = NULL,
                               error_message = NULL
                         WHERE id = :id AND status = 'queued'
                        """)
                .bind("id", id)
                .bind("startedAt", utc(startedAt))
                .fetch()
                .rowsUpdated()
                .map(count -> count > 0);
    }

    public Mono<Void> updateProgress(
            UUID id, int totalItems, int processedItems, int failedItems) {
        return databaseClient
                .sql("""
                        UPDATE memory_jobs
                           SET total_items = :totalItems,
                               processed_items = :processedItems,
                               failed_items = :failedItems
                         WHERE id = :id
                        """)
                .bind("id", id)
                .bind("totalItems", totalItems)
                .bind("processedItems", processedItems)
                .bind("failedItems", failedItems)
                .then();
    }

    public Mono<Void> complete(UUID id, int totalItems, int processedItems, int failedItems) {
        return databaseClient
                .sql("""
                        UPDATE memory_jobs
                           SET status = 'completed',
                               total_items = :totalItems,
                               processed_items = :processedItems,
                               failed_items = :failedItems,
                               finished_at = :finishedAt
                         WHERE id = :id
                        """)
                .bind("id", id)
                .bind("totalItems", totalItems)
                .bind("processedItems", processedItems)
                .bind("failedItems", failedItems)
                .bind("finishedAt", utc(Instant.now(Clock.systemUTC())))
                .then();
    }

    public Mono<Void> fail(UUID id, String errorCode, String errorMessage) {
        return databaseClient
                .sql("""
                        UPDATE memory_jobs
                           SET status = 'failed',
                               error_code = :errorCode,
                               error_message = :errorMessage,
                               finished_at = :finishedAt
                         WHERE id = :id
                        """)
                .bind("id", id)
                .bind("errorCode", errorCode)
                .bind("errorMessage", truncate(errorMessage, 2000))
                .bind("finishedAt", utc(Instant.now(Clock.systemUTC())))
                .then();
    }

    private static String truncate(String value, int length) {
        return value == null || value.length() <= length ? value : value.substring(0, length);
    }

    static MemoryJobEntity map(io.r2dbc.spi.Row row) {
        var entity = new MemoryJobEntity();
        entity.setId(row.get("id", UUID.class));
        entity.setJobType(row.get("job_type", String.class));
        entity.setScopeType(row.get("scope_type", String.class));
        entity.setScopeId(row.get("scope_id", String.class));
        entity.setWatermarkFrom(instant(row.get("watermark_from", OffsetDateTime.class)));
        entity.setWatermarkTo(instant(row.get("watermark_to", OffsetDateTime.class)));
        entity.setStatus(row.get("status", String.class));
        entity.setTotalItems(value(row.get("total_items", Integer.class)));
        entity.setProcessedItems(value(row.get("processed_items", Integer.class)));
        entity.setFailedItems(value(row.get("failed_items", Integer.class)));
        entity.setIdempotencyKey(row.get("idempotency_key", String.class));
        entity.setCreatedAt(instant(row.get("created_at", OffsetDateTime.class)));
        entity.setStartedAt(instant(row.get("started_at", OffsetDateTime.class)));
        entity.setFinishedAt(instant(row.get("finished_at", OffsetDateTime.class)));
        entity.setErrorCode(row.get("error_code", String.class));
        entity.setErrorMessage(row.get("error_message", String.class));
        return entity;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
