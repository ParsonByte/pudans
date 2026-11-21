package cn.tolim.pudans.infra.r2dbc.ai.runtime;

import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiAgentSnapshotRepository {

    private static final String COLUMNS = "id, agent_name, name, size_bytes, created_by, created_at";

    private final DatabaseClient databaseClient;

    public Mono<SnapshotRecord> insert(
            String agentName, String name, String createdBy, byte[] content) {
        return databaseClient.sql("""
                        INSERT INTO ai_agent_snapshot (agent_name, name, content, size_bytes, created_by)
                        VALUES (:agentName, :name, :content, :sizeBytes, :createdBy)
                        RETURNING
                        """ + COLUMNS)
                .bind("agentName", agentName)
                .bind("name", name)
                .bind("content", content)
                .bind("sizeBytes", content.length)
                .bind("createdBy", createdBy)
                .map((row, metadata) -> new SnapshotRecord(
                        row.get("id", Long.class),
                        row.get("agent_name", String.class),
                        row.get("name", String.class),
                        null,
                        row.get("size_bytes", Integer.class),
                        row.get("created_by", String.class),
                        row.get("created_at", Instant.class)))
                .one();
    }

    public Flux<SnapshotRecord> list(String agentName, int limit) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                         FROM ai_agent_snapshot
                         WHERE agent_name = :agentName
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("agentName", agentName)
                .bind("limit", Math.max(1, limit))
                .map((row, metadata) -> new SnapshotRecord(
                        row.get("id", Long.class),
                        row.get("agent_name", String.class),
                        row.get("name", String.class),
                        null,
                        row.get("size_bytes", Integer.class),
                        row.get("created_by", String.class),
                        row.get("created_at", Instant.class)))
                .all();
    }

    public Mono<SnapshotRecord> find(long id, String agentName) {
        return databaseClient.sql("SELECT " + COLUMNS + ", content FROM ai_agent_snapshot"
                        + " WHERE id = :id AND agent_name = :agentName")
                .bind("id", id)
                .bind("agentName", agentName)
                .map((row, metadata) -> new SnapshotRecord(
                        row.get("id", Long.class),
                        row.get("agent_name", String.class),
                        row.get("name", String.class),
                        row.get("content", byte[].class),
                        row.get("size_bytes", Integer.class),
                        row.get("created_by", String.class),
                        row.get("created_at", Instant.class)))
                .one();
    }

    public Mono<Boolean> delete(long id, String agentName) {
        return databaseClient.sql("DELETE FROM ai_agent_snapshot WHERE id = :id AND agent_name = :agentName")
                .bind("id", id)
                .bind("agentName", agentName)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    public record SnapshotRecord(
            Long id,
            String agentName,
            String name,
            @Nullable byte[] content,
            Integer sizeBytes,
            @Nullable String createdBy,
            Instant createdAt) {}
}