package cn.tolim.pudans.infra.r2dbc.ai.runtime;

import cn.tolim.pudans.contract.ai.resource.AiSandboxProvider;
import cn.tolim.pudans.core.ai.runtime.sandbox.SandboxState;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
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
public class AiAgentRuntimeRepository {

    private static final String COLUMNS = """
            agent_name, provider, sandbox_id, state, image, entrypoint, cpu, memory_mb,
            timeout_seconds, endpoint, last_error, config, created_at, updated_at, last_active_at
            """;

    private final DatabaseClient databaseClient;

    public Mono<RuntimeRecord> find(String agentName) {
        return databaseClient.sql("SELECT " + COLUMNS + " FROM ai_agent_runtime WHERE agent_name = :agentName")
                .bind("agentName", agentName)
                .map((row, metadata) -> mapRow(row))
                .one();
    }

    public Flux<RuntimeRecord> listBefore(long beforeMillis, int limit) {
        return databaseClient.sql("SELECT " + COLUMNS + """
                        FROM ai_agent_runtime
                        WHERE EXTRACT(EPOCH FROM updated_at) * 1000 < :beforeMillis
                        ORDER BY updated_at DESC, agent_name DESC
                        LIMIT :limit
                        """)
                .bind("beforeMillis", beforeMillis)
                .bind("limit", limit)
                .map((row, metadata) -> mapRow(row))
                .all();
    }

    public Mono<RuntimeRecord> upsert(RuntimeRecord record) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_agent_runtime (
                            agent_name, provider, sandbox_id, state, image, entrypoint, cpu, memory_mb,
                            timeout_seconds, endpoint, last_error, config, last_active_at
                        ) VALUES (
                            :agentName, :provider, :sandboxId, :state, :image, :entrypoint, :cpu, :memoryMb,
                            :timeoutSeconds, :endpoint, :lastError, CAST(:config AS jsonb), :lastActiveAt
                        )
                        ON CONFLICT (agent_name) DO UPDATE SET
                            provider = EXCLUDED.provider,
                            sandbox_id = EXCLUDED.sandbox_id,
                            state = EXCLUDED.state,
                            image = EXCLUDED.image,
                            entrypoint = EXCLUDED.entrypoint,
                            cpu = EXCLUDED.cpu,
                            memory_mb = EXCLUDED.memory_mb,
                            timeout_seconds = EXCLUDED.timeout_seconds,
                            endpoint = EXCLUDED.endpoint,
                            last_error = EXCLUDED.last_error,
                            config = EXCLUDED.config,
                            updated_at = now()
                        RETURNING """ + " " + COLUMNS)
                .bind("agentName", record.agentName())
                .bind("provider", record.provider().name())
                .bind("state", record.state().name());
        spec = AiR2dbcBindings.bindNullable(spec, "sandboxId", record.sandboxId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "image", record.image(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "entrypoint", record.entrypoint(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "cpu", record.cpu(), Double.class);
        spec = AiR2dbcBindings.bindNullable(spec, "memoryMb", record.memoryMb(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "timeoutSeconds", record.timeoutSeconds(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "endpoint", record.endpoint(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "lastError", record.lastError(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "config", record.config(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "lastActiveAt", record.lastActiveAt(), Instant.class);
        return spec.map((row, metadata) -> mapRow(row)).one();
    }

    public Mono<RuntimeRecord> updateState(
            String agentName,
            SandboxState state,
            @Nullable String sandboxId,
            @Nullable String endpoint,
            @Nullable String lastError,
            boolean touch) {
        var spec = databaseClient.sql("""
                        UPDATE ai_agent_runtime
                        SET state = :state,
                            sandbox_id = :sandboxId,
                            endpoint = :endpoint,
                            last_error = :lastError,
                            last_active_at = CASE WHEN :touch THEN now() ELSE last_active_at END,
                            updated_at = now()
                        WHERE agent_name = :agentName
                        RETURNING """ + " " + COLUMNS)
                .bind("state", state.name())
                .bind("touch", touch);
        spec = AiR2dbcBindings.bindNullable(spec, "sandboxId", sandboxId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "endpoint", endpoint, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "lastError", lastError, String.class);
        return spec.bind("agentName", agentName).map((row, metadata) -> mapRow(row)).one();
    }

    public Mono<Void> markActive(String agentName) {
        return databaseClient.sql("""
                        UPDATE ai_agent_runtime
                        SET last_active_at = now(), updated_at = now()
                        WHERE agent_name = :agentName
                        """)
                .bind("agentName", agentName)
                .fetch()
                .rowsUpdated()
                .then();
    }

    public Mono<Boolean> delete(String agentName) {
        return databaseClient.sql("DELETE FROM ai_agent_runtime WHERE agent_name = :agentName")
                .bind("agentName", agentName)
                .fetch()
                .rowsUpdated()
                .map(updated -> updated > 0);
    }

    static RuntimeRecord mapRow(Row row) {
        return new RuntimeRecord(
                row.get("agent_name", String.class),
                AiSandboxProvider.Provider.valueOf(row.get("provider", String.class)),
                row.get("sandbox_id", String.class),
                SandboxState.valueOf(row.get("state", String.class)),
                row.get("image", String.class),
                row.get("entrypoint", String.class),
                row.get("cpu", Double.class),
                row.get("memory_mb", Integer.class),
                row.get("timeout_seconds", Integer.class),
                row.get("endpoint", String.class),
                row.get("last_error", String.class),
                AiR2dbcBindings.jsonText(row.get("config", String.class)),
                row.get("created_at", Instant.class),
                row.get("updated_at", Instant.class),
                row.get("last_active_at", Instant.class));
    }

    public record RuntimeRecord(
            String agentName,
            AiSandboxProvider.Provider provider,
            @Nullable String sandboxId,
            SandboxState state,
            @Nullable String image,
            @Nullable String entrypoint,
            @Nullable Double cpu,
            @Nullable Integer memoryMb,
            @Nullable Integer timeoutSeconds,
            @Nullable String endpoint,
            @Nullable String lastError,
            @Nullable String config,
            @Nullable Instant createdAt,
            @Nullable Instant updatedAt,
            @Nullable Instant lastActiveAt) {

        public RuntimeRecord {
            if (agentName == null || agentName.isBlank()) {
                throw new IllegalArgumentException("agentName 不能为空");
            }
            if (provider == null) {
                throw new IllegalArgumentException("provider 不能为空");
            }
            if (state == null) {
                throw new IllegalArgumentException("state 不能为空");
            }
        }
    }
}