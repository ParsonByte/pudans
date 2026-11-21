package cn.tolim.pudans.core.infra.r2dbc.ai.memory;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.core.ai.memory.MemoryGenerationLog;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiR2dbcBindings;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiMemoryGenerationLogRepository {

    private static final String SELECT_COLUMNS =
            "id, memory_level, memory_id, team_id, agent_id, user_id, session_id, prompt_id, "
                    + "prompt_version, prompt_source, prompt_hash, model_code, input_ref, output_ref, created_at";

    private final DatabaseClient databaseClient;

    public Mono<Void> insert(MemoryGenerationLog log) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_memory_generation_log (
                            memory_level, memory_id, team_id, agent_id, user_id, session_id,
                            prompt_id, prompt_version, prompt_source, prompt_hash, model_code,
                            input_ref, output_ref
                        ) VALUES (
                            :memoryLevel, :memoryId, :teamId, :agentId, :userId, :sessionId,
                            :promptId, :promptVersion, :promptSource, :promptHash, :modelCode,
                            :inputRef, :outputRef
                        )
                        """)
                .bind("memoryLevel", log.memoryLevel().name())
                .bind("memoryId", log.memoryId())
                .bind("promptSource", log.promptSource().name().toLowerCase(Locale.ROOT))
                .bind("promptHash", log.promptHash());
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", log.teamId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agentId", log.agentId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "userId", log.userId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "sessionId", log.sessionId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "promptId", log.promptId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "promptVersion", log.promptVersion(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "modelCode", log.modelCode(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "inputRef", log.inputRef(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "outputRef", log.outputRef(), String.class);
        return spec.then();
    }

    public Flux<LogEntry> listByScope(
            String teamId,
            String agentId,
            AiMemoryPrompt.@Nullable Layer layer,
            @Nullable String status,
            @Nullable Instant from,
            @Nullable Instant to,
            long beforeId,
            int limit) {
        var spec = databaseClient.sql("SELECT " + SELECT_COLUMNS + """
                         FROM ai_memory_generation_log
                         WHERE team_id = :teamId
                           AND agent_id = :agentId
                           AND id < :beforeId
                           AND (:layer IS NULL OR memory_level = :layer)
                           AND (:status IS NULL OR prompt_source = :status)
                           AND (:from IS NULL OR created_at >= :from)
                           AND (:to IS NULL OR created_at < :to)
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("teamId", teamId)
                .bind("agentId", agentId)
                .bind("beforeId", beforeId)
                .bind("limit", limit);
        spec = AiR2dbcBindings.bindNullable(
                spec, "layer", layer == null ? null : layer.name(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "status", status == null ? null : status.toLowerCase(Locale.ROOT), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "from",
                from == null ? null : OffsetDateTime.ofInstant(from, ZoneOffset.UTC),
                OffsetDateTime.class);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "to",
                to == null ? null : OffsetDateTime.ofInstant(to, ZoneOffset.UTC),
                OffsetDateTime.class);
        return spec.map((row, metadata) -> mapEntry(row)).all();
    }

    public Mono<LogEntry> findById(long id) {
        return databaseClient.sql("SELECT " + SELECT_COLUMNS + " FROM ai_memory_generation_log WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> mapEntry(row))
                .one();
    }

    private static LogEntry mapEntry(io.r2dbc.spi.Row row) {
        return new LogEntry(
                row.get("id", Long.class),
                AiMemoryPrompt.Layer.valueOf(row.get("memory_level", String.class)),
                row.get("memory_id", Long.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("user_id", String.class),
                row.get("session_id", String.class),
                row.get("prompt_id", String.class),
                row.get("prompt_version", Integer.class),
                row.get("prompt_source", String.class),
                row.get("prompt_hash", String.class),
                row.get("model_code", String.class),
                row.get("input_ref", String.class),
                row.get("output_ref", String.class),
                row.get("created_at", Instant.class));
    }

    public record LogEntry(
            Long id,
            AiMemoryPrompt.Layer memoryLevel,
            @Nullable Long memoryId,
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String userId,
            @Nullable String sessionId,
            @Nullable String promptId,
            @Nullable Integer promptVersion,
            @Nullable String promptSource,
            @Nullable String promptHash,
            @Nullable String modelCode,
            @Nullable String inputRef,
            @Nullable String outputRef,
            @Nullable Instant createdAt) {}
}