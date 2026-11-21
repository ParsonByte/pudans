package cn.tolim.pudans.core.infra.r2dbc.ai.memory;

import cn.tolim.pudans.contract.ai.AiMemoryPrompt;
import cn.tolim.pudans.contract.ai.AiMemoryPromptBinding;
import cn.tolim.pudans.core.ai.memory.prompt.MemoryPromptLog;
import cn.tolim.pudans.core.infra.r2dbc.ai.chat.AiR2dbcBindings;
import io.r2dbc.spi.Row;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiMemoryPromptLogRepository {

    private static final String SELECT_COLUMNS =
            "id, prompt_id, version, action, target_type, team, agent, layer, actor, created_at";

    private final DatabaseClient databaseClient;

    public Mono<MemoryPromptLog> insert(MemoryPromptLog log) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_memory_prompt_log (
                            prompt_id, version, action, target_type, team, agent, layer, actor, created_at
                        ) VALUES (
                            :promptId, :version, :action, :targetType, :team, :agent, :layer, :actor,
                            COALESCE(:createdAt, now())
                        )
                        RETURNING
                        """ + SELECT_COLUMNS)
                .bind("promptId", log.promptId())
                .bind("action", log.action().name());
        spec = AiR2dbcBindings.bindNullable(spec, "version", log.version(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "targetType",
                log.targetType() == null ? null : log.targetType().name(),
                String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "team", log.team(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agent", log.agent(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "layer", log.layer() == null ? null : log.layer().name(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "actor", log.actor(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec,
                "createdAt",
                log.createdAt() == null ? null : OffsetDateTime.ofInstant(log.createdAt(), ZoneOffset.UTC),
                OffsetDateTime.class);
        return spec.map((row, metadata) -> mapLog(row)).one();
    }

    public Flux<MemoryPromptLog> list(
            @Nullable String promptId,
            @Nullable Instant from,
            @Nullable Instant to,
            long beforeId,
            int limit) {
        var spec = databaseClient.sql("SELECT " + SELECT_COLUMNS + """
                         FROM ai_memory_prompt_log
                         WHERE id < :beforeId
                           AND (:promptId IS NULL OR prompt_id = :promptId)
                           AND (:from IS NULL OR created_at >= :from)
                           AND (:to IS NULL OR created_at < :to)
                         ORDER BY id DESC
                         LIMIT :limit
                         """)
                .bind("beforeId", beforeId)
                .bind("limit", limit);
        spec = AiR2dbcBindings.bindNullable(spec, "promptId", promptId, String.class);
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
        return spec.map((row, metadata) -> mapLog(row)).all();
    }

    private static MemoryPromptLog mapLog(Row row) {
        var targetType = row.get("target_type", String.class);
        var layer = row.get("layer", String.class);
        return new MemoryPromptLog(
                row.get("id", Long.class),
                row.get("prompt_id", String.class),
                row.get("version", Integer.class),
                MemoryPromptLog.Action.valueOf(row.get("action", String.class)),
                targetType == null ? null : AiMemoryPromptBinding.TargetType.valueOf(targetType),
                row.get("team", String.class),
                row.get("agent", String.class),
                layer == null ? null : AiMemoryPrompt.Layer.valueOf(layer),
                row.get("actor", String.class),
                row.get("created_at", Instant.class));
    }
}