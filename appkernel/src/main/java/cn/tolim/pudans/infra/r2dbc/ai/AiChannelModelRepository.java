package cn.tolim.pudans.infra.r2dbc.ai;

import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.bindNullable;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.duplicateAs;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.instant;
import static cn.tolim.pudans.infra.r2dbc.ai.AiSqlSupport.utc;

import cn.tolim.pudans.contract.ai.AiChannelModel;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiChannelModelRepository {

    private static final String COLUMNS = """
            SELECT id, channel_id, model_id, upstream_model_key, priority, weight, enabled,
                   parameter_overrides_jsonb::text AS parameter_overrides,
                   provider_metadata_jsonb::text AS provider_metadata,
                   created_at, updated_at
              FROM ai_channel_models
            """;

    private static final String ENABLED_ROUTE = """
            SELECT m.id, m.channel_id, m.model_id, m.upstream_model_key, m.priority,
                   m.weight, m.enabled,
                   m.parameter_overrides_jsonb::text AS parameter_overrides,
                   m.provider_metadata_jsonb::text AS provider_metadata,
                   m.created_at, m.updated_at
              FROM ai_channel_models m
              JOIN ai_channels c ON c.id = m.channel_id
             WHERE m.model_id = :modelId
               AND m.enabled = TRUE
               AND c.workspace_id = :workspaceId
               AND c.status = 'active'
             ORDER BY m.priority ASC NULLS LAST, m.weight DESC NULLS LAST, m.id
             LIMIT 1
            """;

    private static final String INSERT = """
            INSERT INTO ai_channel_models (
                id, channel_id, model_id, upstream_model_key, priority, weight, enabled,
                parameter_overrides_jsonb, provider_metadata_jsonb, created_at, updated_at)
            VALUES (
                :id, :channelId, :modelId, :upstreamModelKey, :priority, :weight, :enabled,
                CAST(:parameterOverrides AS jsonb), CAST(:providerMetadata AS jsonb),
                :createdAt, :updatedAt)
            """;

    private static final String UPDATE = """
            UPDATE ai_channel_models
               SET channel_id = :channelId,
                   model_id = :modelId,
                   upstream_model_key = :upstreamModelKey,
                   priority = :priority,
                   weight = :weight,
                   enabled = :enabled,
                   parameter_overrides_jsonb = CAST(:parameterOverrides AS jsonb),
                   provider_metadata_jsonb = CAST(:providerMetadata AS jsonb),
                   updated_at = :updatedAt
             WHERE id = :id
            """;

    private final DatabaseClient databaseClient;

    public Flux<AiChannelModel> findAll() {
        return databaseClient
                .sql(COLUMNS + " ORDER BY priority, id")
                .map((row, metadata) -> map(row))
                .all();
    }

    public Flux<AiChannelModel> findByChannelId(UUID channelId) {
        return databaseClient
                .sql(COLUMNS + " WHERE channel_id = :channelId ORDER BY priority, id")
                .bind("channelId", channelId)
                .map((row, metadata) -> map(row))
                .all();
    }

    public Mono<AiChannelModel> findById(UUID id) {
        return databaseClient
                .sql(COLUMNS + " WHERE id = :id")
                .bind("id", id)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiChannelModel> findByChannelIdAndModelId(UUID channelId, UUID modelId) {
        return databaseClient
                .sql(COLUMNS + " WHERE channel_id = :channelId AND model_id = :modelId")
                .bind("channelId", channelId)
                .bind("modelId", modelId)
                .map((row, metadata) -> map(row))
                .one();
    }

    // 联合渠道状态与工作区筛选当前模型可用的最高优先级路由。
    public Mono<AiChannelModel> findEnabledRoute(UUID modelId, String workspaceId) {
        return databaseClient
                .sql(ENABLED_ROUTE)
                .bind("modelId", modelId)
                .bind("workspaceId", workspaceId)
                .map((row, metadata) -> map(row))
                .one();
    }

    public Mono<AiChannelModel> insert(AiChannelModel model) {
        return duplicateAs(
                databaseClient
                        .sql(INSERT)
                        .bind("id", model.id())
                        .bind("channelId", model.channelId())
                        .bind("modelId", model.modelId())
                        .bind("upstreamModelKey", model.upstreamModelKey())
                        .bind("priority", model.priority())
                        .bind("weight", model.weight())
                        .bind("enabled", model.enabled())
                        .bind("parameterOverrides", AiJsonCodec.write(model.parameterOverrides()))
                        .bind("providerMetadata", AiJsonCodec.write(model.providerMetadata()))
                        .bind("createdAt", utc(model.createdAt()))
                        .bind("updatedAt", utc(model.updatedAt()))
                        .then()
                        .thenReturn(model),
                "ai-channel-model-conflict",
                "渠道与模型映射已存在。");
    }

    public Mono<AiChannelModel> update(AiChannelModel model) {
        return duplicateAs(
                databaseClient
                        .sql(UPDATE)
                        .bind("id", model.id())
                        .bind("channelId", model.channelId())
                        .bind("modelId", model.modelId())
                        .bind("upstreamModelKey", model.upstreamModelKey())
                        .bind("priority", model.priority())
                        .bind("weight", model.weight())
                        .bind("enabled", model.enabled())
                        .bind("parameterOverrides", AiJsonCodec.write(model.parameterOverrides()))
                        .bind("providerMetadata", AiJsonCodec.write(model.providerMetadata()))
                        .bind("updatedAt", utc(model.updatedAt()))
                        .then()
                        .thenReturn(model),
                "ai-channel-model-conflict",
                "渠道与模型映射已存在。");
    }

    public Mono<Void> delete(UUID id) {
        return databaseClient
                .sql("DELETE FROM ai_channel_models WHERE id = :id")
                .bind("id", id)
                .then();
    }

    public Mono<Boolean> existsByChannelId(UUID channelId) {
        return exists("SELECT EXISTS (SELECT 1 FROM ai_channel_models WHERE channel_id = :id)", channelId);
    }

    public Mono<Boolean> existsByModelId(UUID modelId) {
        return exists("SELECT EXISTS (SELECT 1 FROM ai_channel_models WHERE model_id = :id)", modelId);
    }

    private Mono<Boolean> exists(String sql, UUID id) {
        return databaseClient
                .sql(sql)
                .bind("id", id)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one()
                .defaultIfEmpty(false);
    }

    private static AiChannelModel map(io.r2dbc.spi.Row row) {
        return new AiChannelModel(
                row.get("id", UUID.class),
                row.get("channel_id", UUID.class),
                row.get("model_id", UUID.class),
                row.get("upstream_model_key", String.class),
                row.get("priority", Integer.class),
                row.get("weight", Integer.class),
                Boolean.TRUE.equals(row.get("enabled", Boolean.class)),
                AiJsonCodec.readObject(row.get("parameter_overrides", String.class)),
                AiJsonCodec.readObject(row.get("provider_metadata", String.class)),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("updated_at", OffsetDateTime.class)));
    }
}
