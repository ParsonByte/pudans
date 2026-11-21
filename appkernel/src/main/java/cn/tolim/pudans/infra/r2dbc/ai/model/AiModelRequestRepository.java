package cn.tolim.pudans.infra.r2dbc.ai.model;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiR2dbcBindings;
import com.fasterxml.jackson.databind.JsonNode;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
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
public class AiModelRequestRepository {

    private final DatabaseClient databaseClient;

    public Mono<ModelRequestRow> create(NewModelRequest request) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_model_request (
                            request_id, space_id, user_id, team_id, agent_id, session_id,
                            idempotency_key, route_group, model_type, capability,
                            selected_provider, selected_account, selected_model, tier, plan_id, billing_scope,
                            request_payload
                        ) VALUES (
                            :requestId, :spaceId, :userId, :teamId, :agentId, :sessionId,
                            :idempotencyKey, :routeGroup, :modelType, :capability,
                            :selectedProvider, :selectedAccount, :selectedModel, :tier,
                            :planId, :billingScope, CAST(:requestPayload AS jsonb)
                        )
                        RETURNING *
                        """)
                .bind("requestId", request.requestId())
                .bind("spaceId", request.spaceId())
                .bind("userId", request.userId())
                .bind("idempotencyKey", request.idempotencyKey())
                .bind("modelType", request.modelType())
                .bind("capability", request.capability())
                .bind("requestPayload", request.requestPayload());
        spec = AiR2dbcBindings.bindNullable(spec, "teamId", request.teamId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "agentId", request.agentId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "sessionId", request.sessionId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "routeGroup", request.routeGroup(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "selectedProvider", request.selectedProvider(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "selectedAccount", request.selectedAccount(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "selectedModel", request.selectedModel(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "tier", request.tier(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "planId", request.planId(), String.class);
        spec = spec.bind("billingScope", request.billingScope());
        return spec.map((row, metadata) -> row(row)).one();
    }

    public Mono<ModelRequestRow> complete(ModelCompletion completion) {
        var spec = databaseClient.sql("""
                        UPDATE ai_model_request
                        SET status = :status,
                            error_code = :errorCode,
                            error_message = :errorMessage,
                            provider_task_id = :providerTaskId,
                            input_tokens = :inputTokens,
                            cache_tokens = :cacheTokens,
                            output_tokens = :outputTokens,
                            image_count = :imageCount,
                            video_seconds = :videoSeconds,
                            audio_seconds = :audioSeconds,
                            speech_characters = :speechCharacters,
                            task_count = :taskCount,
                            usage_units = :usageUnits,
                            billing_source = :billingSource,
                            billing_status = :billingStatus,
                            latency_ms = :latencyMs,
                            usage_source = :usageSource,
                            upstream_request_id = :upstreamRequestId,
                            result_payload = CAST(:resultPayload AS jsonb),
                            completed_at = now()
                        WHERE request_id = :requestId
                        RETURNING *
                        """)
                .bind("status", completion.status())
                .bind("billingStatus", completion.billingStatus())
                .bind("requestId", completion.requestId())
                .bind("resultPayload", completion.resultPayload() == null ? "{}" : completion.resultPayload());
        spec = AiR2dbcBindings.bindNullable(spec, "errorCode", completion.errorCode(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "errorMessage", completion.errorMessage(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "providerTaskId", completion.providerTaskId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "inputTokens", completion.inputTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "cacheTokens", completion.cacheTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "outputTokens", completion.outputTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "imageCount", completion.imageCount(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "videoSeconds", completion.videoSeconds(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "audioSeconds", completion.audioSeconds(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "speechCharacters", completion.speechCharacters(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "taskCount", completion.taskCount(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "usageUnits", completion.units(), Long.class);
        spec = AiR2dbcBindings.bindNullable(spec, "billingSource", completion.billingSource(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "latencyMs", completion.latencyMs(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "usageSource", completion.usageSource(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "upstreamRequestId", completion.upstreamRequestId(), String.class);
        return spec.map((row, metadata) -> row(row)).one();
    }

    public Mono<ModelRequestRow> markTask(String requestId, String providerTaskId) {
        return databaseClient.sql("""
                        UPDATE ai_model_request
                        SET provider_task_id = :providerTaskId, status = 'RUNNING'
                        WHERE request_id = :requestId
                        RETURNING *
                        """)
                .bind("providerTaskId", providerTaskId)
                .bind("requestId", requestId)
                .map((row, metadata) -> row(row))
                .one();
    }

    public Mono<ModelRequestRow> findByRequestId(String requestId) {
        return databaseClient.sql("""
                        SELECT * FROM ai_model_request WHERE request_id = :requestId
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> row(row))
                .one();
    }

    public Mono<ModelRequestRow> findByUserAndIdempotency(String userId, String idempotencyKey) {
        return databaseClient.sql("""
                        SELECT * FROM ai_model_request
                        WHERE user_id = :userId AND idempotency_key = :idempotencyKey
                        """)
                .bind("userId", userId)
                .bind("idempotencyKey", idempotencyKey)
                .map((row, metadata) -> row(row))
                .one();
    }

    public Mono<ModelRequestRow> findByRequestIdAndUser(String requestId, String userId) {
        return databaseClient.sql("""
                        SELECT * FROM ai_model_request
                        WHERE request_id = :requestId AND user_id = :userId
                        """)
                .bind("requestId", requestId)
                .bind("userId", userId)
                .map((row, metadata) -> row(row))
                .one();
    }

    public Flux<ModelRequestRow> listByUser(String userId, int limit) {
        return databaseClient.sql("""
                        SELECT * FROM ai_model_request
                        WHERE user_id = :userId
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("userId", userId)
                .bind("limit", limit)
                .map((row, metadata) -> row(row))
                .all();
    }

    public Flux<ModelStatsRow> stats(int days) {
        return databaseClient.sql("""
                        SELECT model_type, capability, COALESCE(selected_model, 'unknown') AS model,
                               status,
                               COUNT(*) AS requests,
                               COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units,
                               COALESCE(SUM(image_count), 0) AS images,
                               COALESCE(SUM(video_seconds), 0) AS video_seconds,
                               COALESCE(SUM(audio_seconds), 0) AS audio_seconds,
                               COALESCE(SUM(speech_characters), 0) AS speech_characters,
                               COALESCE(SUM(task_count), 0) AS tasks
                        FROM ai_model_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        GROUP BY model_type, capability, selected_model, status
                        ORDER BY requests DESC
                        """)
                .bind("days", days)
                .map((row, metadata) -> new ModelStatsRow(
                        row.get("model_type", String.class),
                        row.get("capability", String.class),
                        row.get("model", String.class),
                        row.get("status", String.class),
                        count(row, "requests"),
                        decimal(row, "units"),
                        count(row, "images"),
                        count(row, "video_seconds"),
                        count(row, "audio_seconds"),
                        count(row, "speech_characters"),
                        count(row, "tasks")))
                .all();
    }

    private static ModelRequestRow row(Row row) {
        return new ModelRequestRow(
                row.get("id", Long.class),
                row.get("request_id", String.class),
                row.get("space_id", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("session_id", String.class),
                row.get("idempotency_key", String.class),
                row.get("route_group", String.class),
                row.get("model_type", String.class),
                row.get("capability", String.class),
                row.get("selected_provider", String.class),
                row.get("selected_account", String.class),
                row.get("selected_model", String.class),
                row.get("tier", String.class),
                row.get("status", String.class),
                row.get("error_code", String.class),
                row.get("error_message", String.class),
                row.get("provider_task_id", String.class),
                row.get("input_tokens", Integer.class),
                row.get("cache_tokens", Integer.class),
                row.get("output_tokens", Integer.class),
                row.get("image_count", Integer.class),
                row.get("video_seconds", Integer.class),
                row.get("audio_seconds", Integer.class),
                row.get("speech_characters", Integer.class),
                row.get("task_count", Integer.class),
                row.get("usage_units", Long.class),
                row.get("plan_id", String.class),
                row.get("billing_scope", String.class),
                row.get("billing_source", String.class),
                row.get("billing_status", String.class),
                row.get("latency_ms", Integer.class),
                row.get("usage_source", String.class),
                row.get("upstream_request_id", String.class),
                json(row.get("request_payload", Object.class)),
                json(row.get("result_payload", Object.class)),
                json(row.get("route_trace", Object.class)),
                instant(row.get("created_at", OffsetDateTime.class)),
                instant(row.get("completed_at", OffsetDateTime.class)));
    }

    private static JsonNode json(@Nullable Object value) {
        var text = AiR2dbcBindings.jsonText(value);
        return text == null || text.isBlank() ? null : JsonUtils.jsonToObject(text, JsonNode.class);
    }

    private static long count(Row row, String column) {
        var value = row.get(column, Long.class);
        return value == null ? 0 : value;
    }

    private static BigDecimal decimal(Row row, String column) {
        var value = row.get(column, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Instant instant(@Nullable OffsetDateTime value) {
        return value == null ? Instant.EPOCH : value.toInstant();
    }

    public record NewModelRequest(
            String requestId,
            String spaceId,
            String userId,
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String sessionId,
            String idempotencyKey,
            @Nullable String routeGroup,
            String modelType,
            String capability,
            @Nullable String selectedProvider,
            @Nullable String selectedAccount,
            @Nullable String selectedModel,
            @Nullable String tier,
            @Nullable String planId,
            String billingScope,
            String requestPayload) {}

    public record ModelCompletion(
            String requestId,
            String status,
            @Nullable String errorCode,
            @Nullable String errorMessage,
            @Nullable String providerTaskId,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable Integer imageCount,
            @Nullable Integer videoSeconds,
            @Nullable Integer audioSeconds,
            @Nullable Integer speechCharacters,
            @Nullable Integer taskCount,
            @Nullable Long units,
            @Nullable String billingSource,
            String billingStatus,
            @Nullable Integer latencyMs,
            @Nullable String usageSource,
            @Nullable String upstreamRequestId,
            @Nullable String resultPayload) {}

    public record ModelRequestRow(
            long id,
            String requestId,
            String spaceId,
            String userId,
            @Nullable String teamId,
            @Nullable String agentId,
            @Nullable String sessionId,
            String idempotencyKey,
            @Nullable String routeGroup,
            String modelType,
            String capability,
            @Nullable String selectedProvider,
            @Nullable String selectedAccount,
            @Nullable String selectedModel,
            @Nullable String tier,
            String status,
            @Nullable String errorCode,
            @Nullable String errorMessage,
            @Nullable String providerTaskId,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable Integer imageCount,
            @Nullable Integer videoSeconds,
            @Nullable Integer audioSeconds,
            @Nullable Integer speechCharacters,
            @Nullable Integer taskCount,
            @Nullable Long units,
            @Nullable String planId,
            String billingScope,
            @Nullable String billingSource,
            String billingStatus,
            @Nullable Integer latencyMs,
            @Nullable String usageSource,
            @Nullable String upstreamRequestId,
            @Nullable JsonNode requestPayload,
            @Nullable JsonNode resultPayload,
            @Nullable JsonNode routeTrace,
            Instant createdAt,
            @Nullable Instant completedAt) {}

    public record ModelStatsRow(
            String modelType,
            String capability,
            String model,
            String status,
            long requests,
            BigDecimal units,
            long images,
            long videoSeconds,
            long audioSeconds,
            long speechCharacters,
            long tasks) {}
}
