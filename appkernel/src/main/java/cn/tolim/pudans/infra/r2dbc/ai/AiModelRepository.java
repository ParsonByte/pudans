package cn.tolim.pudans.infra.r2dbc.ai;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.contract.ai.AiModel;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiModelRepository {

    private static final String COLUMNS = """
            SELECT id, logical_name, display_name, description,
                   capabilities_jsonb::text AS capabilities,
                   context_window, max_output_tokens,
                   parameter_schema_jsonb::text AS parameter_schema,
                   status, created_at, updated_at
              FROM ai_models
            """;

    private static final String INSERT = """
            INSERT INTO ai_models (
                id, logical_name, display_name, description, capabilities_jsonb,
                context_window, max_output_tokens, parameter_schema_jsonb, status,
                created_at, updated_at)
            VALUES (
                :id, :logicalName, :displayName, :description, CAST(:capabilities AS jsonb),
                :contextWindow, :maxOutputTokens, CAST(:parameterSchema AS jsonb), :status,
                :createdAt, :updatedAt)
            """;

    private static final String UPDATE = """
            UPDATE ai_models
               SET logical_name = :logicalName,
                   display_name = :displayName,
                   description = :description,
                   capabilities_jsonb = CAST(:capabilities AS jsonb),
                   context_window = :contextWindow,
                   max_output_tokens = :maxOutputTokens,
                   parameter_schema_jsonb = CAST(:parameterSchema AS jsonb),
                   status = :status,
                   updated_at = :updatedAt
             WHERE id = :id
            """;

    private final DatabaseClient databaseClient;

    public Flux<AiModel> findAll() {
        return databaseClient
                .sql(COLUMNS + " ORDER BY logical_name, id")
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<AiModel> findAvailable(String workspaceId) {
        return databaseClient
                .sql(COLUMNS + """
                         WHERE status = 'active'
                           AND EXISTS (
                               SELECT 1
                                 FROM ai_channel_models channel_model
                                 JOIN ai_channels channel ON channel.id = channel_model.channel_id
                                WHERE channel_model.model_id = ai_models.id
                                  AND channel_model.enabled = TRUE
                                  AND channel.status = 'active'
                                  AND channel.workspace_id = :workspaceId)
                         ORDER BY logical_name, id
                        """)
                .bind("workspaceId", workspaceId)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<AiModel> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<Boolean> existsAvailable(UUID id, String workspaceId) {
        return databaseClient
                .sql("""
                        SELECT EXISTS (
                            SELECT 1
                              FROM ai_models model
                              JOIN ai_channel_models channel_model ON channel_model.model_id = model.id
                              JOIN ai_channels channel ON channel.id = channel_model.channel_id
                             WHERE model.id = :id
                               AND model.status = 'active'
                               AND channel_model.enabled = TRUE
                               AND channel.status = 'active'
                               AND channel.workspace_id = :workspaceId)
                        """)
                .bind("id", id)
                .bind("workspaceId", workspaceId)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    public Mono<AiModel> insert(AiModel model) {
        var spec = databaseClient
                .sql(INSERT)
                .bind("id", model.id())
                .bind("logicalName", model.logicalName())
                .bind("displayName", model.displayName());
        spec = bindNullable(spec, "description", model.description(), String.class);
        spec = spec.bind("capabilities", AiJsonCodec.write(model.capabilities()));
        spec = bindNullable(spec, "contextWindow", model.contextWindow(), Integer.class);
        spec = bindNullable(spec, "maxOutputTokens", model.maxOutputTokens(), Integer.class);
        return duplicateAs(
                spec.bind("parameterSchema", AiJsonCodec.write(model.parameterSchema()))
                        .bind("status", model.status())
                        .bind("createdAt", utc(model.createdAt()))
                        .bind("updatedAt", utc(model.updatedAt()))
                        .then()
                        .thenReturn(model),
                "ai-model-name-conflict",
                "AI 模型逻辑名称已存在。");
    }

    public Mono<AiModel> update(AiModel model) {
        var spec = databaseClient
                .sql(UPDATE)
                .bind("id", model.id())
                .bind("logicalName", model.logicalName())
                .bind("displayName", model.displayName());
        spec = bindNullable(spec, "description", model.description(), String.class);
        spec = spec.bind("capabilities", AiJsonCodec.write(model.capabilities()));
        spec = bindNullable(spec, "contextWindow", model.contextWindow(), Integer.class);
        spec = bindNullable(spec, "maxOutputTokens", model.maxOutputTokens(), Integer.class);
        return duplicateAs(
                spec.bind("parameterSchema", AiJsonCodec.write(model.parameterSchema()))
                        .bind("status", model.status())
                        .bind("updatedAt", utc(model.updatedAt()))
                        .then()
                        .thenReturn(model),
                "ai-model-name-conflict",
                "AI 模型逻辑名称已存在。");
    }

    public Mono<Void> delete(UUID id) {
        return databaseClient.sql("DELETE FROM ai_models WHERE id = :id").bind("id", id).then();
    }

    private static AiModel map(io.r2dbc.spi.Row row) {
        return new AiModel(
                row.get("id", UUID.class),
                row.get("logical_name", String.class),
                row.get("display_name", String.class),
                row.get("description", String.class),
                AiJsonCodec.readObject(row.get("capabilities", String.class)),
                row.get("context_window", Integer.class),
                row.get("max_output_tokens", Integer.class),
                AiJsonCodec.readObject(row.get("parameter_schema", String.class)),
                row.get("status", String.class),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }
}
