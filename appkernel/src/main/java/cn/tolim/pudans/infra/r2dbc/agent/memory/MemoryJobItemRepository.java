package cn.tolim.pudans.infra.r2dbc.agent.memory;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

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
public class MemoryJobItemRepository {

    private static final String COLUMNS = """
            SELECT id, job_id, source_id, stage, status, attempt, error_code, error_message,
                   created_at, updated_at
              FROM memory_job_items
            """;

    private final DatabaseClient databaseClient;

    public Mono<MemoryJobItemEntity> insert(MemoryJobItemEntity entity) {
        var spec = databaseClient
                .sql("""
                        INSERT INTO memory_job_items (
                            id, job_id, source_id, stage, status, attempt, error_code,
                            error_message, created_at, updated_at)
                        VALUES (
                            :id, :jobId, :sourceId, :stage, :status, :attempt, :errorCode,
                            :errorMessage, :createdAt, :updatedAt)
                        """)
                .bind("id", entity.getId())
                .bind("jobId", entity.getJobId());
        spec = bindNullable(spec, "sourceId", entity.getSourceId(), String.class);
        spec = bindNullable(spec, "errorCode", entity.getErrorCode(), String.class);
        spec = bindNullable(spec, "errorMessage", entity.getErrorMessage(), String.class);
        return spec.bind("stage", entity.getStage())
                .bind("status", entity.getStatus())
                .bind("attempt", entity.getAttempt())
                .bind("createdAt", utc(entity.getCreatedAt()))
                .bind("updatedAt", utc(entity.getUpdatedAt()))
                .then()
                .thenReturn(entity);
    }

    public Flux<MemoryJobItemEntity> findByJobId(UUID jobId) {
        return databaseClient
                .sql(COLUMNS + " WHERE job_id = :jobId ORDER BY created_at, id")
                .bind("jobId", jobId)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<Void> update(
            UUID id, String status, String errorCode, String errorMessage, Instant updatedAt) {
        var spec = databaseClient
                .sql("""
                        UPDATE memory_job_items
                           SET status = :status,
                               error_code = :errorCode,
                               error_message = :errorMessage,
                               updated_at = :updatedAt
                         WHERE id = :id
                        """)
                .bind("id", id)
                .bind("status", status);
        spec = bindNullable(spec, "errorCode", errorCode, String.class);
        spec = bindNullable(spec, "errorMessage", errorMessage, String.class);
        return spec.bind("updatedAt", utc(updatedAt)).then();
    }

    static MemoryJobItemEntity map(io.r2dbc.spi.Row row) {
        var entity = new MemoryJobItemEntity();
        entity.setId(row.get("id", UUID.class));
        entity.setJobId(row.get("job_id", UUID.class));
        entity.setSourceId(row.get("source_id", String.class));
        entity.setStage(row.get("stage", String.class));
        entity.setStatus(row.get("status", String.class));
        entity.setAttempt(value(row.get("attempt", Integer.class)));
        entity.setErrorCode(row.get("error_code", String.class));
        entity.setErrorMessage(row.get("error_message", String.class));
        entity.setCreatedAt(instant(row.get("created_at", OffsetDateTime.class)));
        entity.setUpdatedAt(instant(row.get("updated_at", OffsetDateTime.class)));
        return entity;
    }

    private static int value(Integer value) {
        return value == null ? 0 : value;
    }
}
