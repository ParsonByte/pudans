package cn.tolim.pudans.infra.r2dbc.ai.chat;

import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
@RequiredArgsConstructor
public class AiChatRequestRepository {

    private final DatabaseClient databaseClient;

    public Mono<ChatRequest> createChatRequest(NewChatRequest request) {
        var spec = databaseClient.sql("""
                        INSERT INTO ai_chat_request (
                            request_id, space_id, user_id, agent_source, team_id, agent_id, session_key,
                            conversation_id, idempotency_key, mode_code, route_group,
                            selected_provider, selected_account, selected_model, tier
                        ) VALUES (
                            :requestId, :spaceId, :userId, :agentSource, :teamId, :agentId, :sessionKey,
                            :conversationId, :idempotencyKey, :modeCode, :routeGroup,
                            :selectedProvider, :selectedAccount, :selectedModel, :tier
                        )
                        RETURNING id, request_id, space_id, user_id, agent_source, team_id, agent_id, session_key,
                                  conversation_id, idempotency_key, mode_code, route_group,
                                  selected_provider, selected_account, selected_model, tier, status,
                                  error_code, input_tokens, cache_tokens, output_tokens, total_tokens,
                                  points, overdraft, billing_source, billing_status, latency_ms, ttft_ms,
                                  tps, usage_source, upstream_request_id, created_at, completed_at
                        """)
                .bind("requestId", request.requestId())
                .bind("spaceId", request.spaceId())
                .bind("userId", request.userId())
                .bind("agentSource", request.agentSource())
                .bind("teamId", request.teamId())
                .bind("agentId", request.agentId())
                .bind("idempotencyKey", request.idempotencyKey());
        spec = AiR2dbcBindings.bindNullable(spec, "sessionKey", request.sessionKey(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "conversationId", request.conversationId(), Long.class);
        spec = AiR2dbcBindings.bindNullable(spec, "modeCode", request.modeCode(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "routeGroup", request.routeGroup(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "selectedProvider", request.selectedProvider(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "selectedAccount", request.selectedAccount(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "selectedModel", request.selectedModel(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "tier", request.tier(), String.class);
        return spec.map((row, metadata) -> mapChatRequest(row)).one();
    }

    public Mono<ChatRequest> completeChatRequest(ChatCompletion completion) {
        var spec = databaseClient.sql("""
                        UPDATE ai_chat_request
                        SET status = :status,
                            error_code = :errorCode,
                            input_tokens = :inputTokens,
                            cache_tokens = :cacheTokens,
                            output_tokens = :outputTokens,
                            total_tokens = :totalTokens,
                            points = :points,
                            billing_source = :billingSource,
                            billing_status = :billingStatus,
                            latency_ms = :latencyMs,
                            ttft_ms = :ttftMs,
                            tps = :tps,
                            usage_source = :usageSource,
                            upstream_request_id = :upstreamRequestId,
                            completed_at = now()
                        WHERE request_id = :requestId
                        RETURNING id, request_id, space_id, user_id, agent_source, team_id, agent_id, session_key,
                                  conversation_id, idempotency_key, mode_code, route_group,
                                  selected_provider, selected_account, selected_model, tier, status,
                                  error_code, input_tokens, cache_tokens, output_tokens, total_tokens,
                                  points, overdraft, billing_source, billing_status, latency_ms, ttft_ms,
                                  tps, usage_source, upstream_request_id, created_at, completed_at
                        """)
                .bind("status", completion.status())
                .bind("billingStatus", completion.billingStatus())
                .bind("requestId", completion.requestId());
        spec = AiR2dbcBindings.bindNullable(spec, "errorCode", completion.errorCode(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "inputTokens", completion.inputTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "cacheTokens", completion.cacheTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "outputTokens", completion.outputTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "totalTokens", completion.totalTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "points", completion.points(), BigDecimal.class);
        spec = AiR2dbcBindings.bindNullable(spec, "billingSource", completion.billingSource(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "latencyMs", completion.latencyMs(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "ttftMs", completion.ttftMs(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "tps", completion.tps(), BigDecimal.class);
        spec = AiR2dbcBindings.bindNullable(spec, "usageSource", completion.usageSource(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "upstreamRequestId", completion.upstreamRequestId(), String.class);
        return spec.map((row, metadata) -> mapChatRequest(row)).one();
    }

    static ChatRequest mapChatRequest(Row row) {
        return new ChatRequest(
                row.get("id", Long.class),
                row.get("request_id", String.class),
                row.get("space_id", String.class),
                row.get("user_id", String.class),
                row.get("agent_source", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("session_key", String.class),
                row.get("conversation_id", Long.class),
                row.get("idempotency_key", String.class),
                row.get("mode_code", String.class),
                row.get("route_group", String.class),
                row.get("selected_provider", String.class),
                row.get("selected_account", String.class),
                row.get("selected_model", String.class),
                row.get("tier", String.class),
                row.get("status", String.class),
                row.get("error_code", String.class),
                row.get("input_tokens", Integer.class),
                row.get("cache_tokens", Integer.class),
                row.get("output_tokens", Integer.class),
                row.get("total_tokens", Integer.class),
                row.get("points", BigDecimal.class),
                row.get("overdraft", BigDecimal.class),
                row.get("billing_source", String.class),
                row.get("billing_status", String.class),
                row.get("latency_ms", Integer.class),
                row.get("ttft_ms", Integer.class),
                row.get("tps", BigDecimal.class),
                row.get("usage_source", String.class),
                row.get("upstream_request_id", String.class),
                row.get("created_at", Instant.class),
                row.get("completed_at", Instant.class));
    }

    public record NewChatRequest(
            String requestId,
            String spaceId,
            String userId,
            String agentSource,
            String teamId,
            String agentId,
            @Nullable String sessionKey,
            @Nullable Long conversationId,
            String idempotencyKey,
            @Nullable String modeCode,
            @Nullable String routeGroup,
            @Nullable String selectedProvider,
            @Nullable String selectedAccount,
            @Nullable String selectedModel,
            @Nullable String tier) {

        public NewChatRequest {
            requireText(requestId, "requestId");
            requireText(spaceId, "spaceId");
            requireText(userId, "userId");
            agentSource = agentSource == null || agentSource.isBlank() ? "pudans-chat" : agentSource;
            requireText(teamId, "teamId");
            requireText(agentId, "agentId");
            requireText(idempotencyKey, "idempotencyKey");
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }

    public record ChatCompletion(
            String requestId,
            String status,
            @Nullable String errorCode,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable Integer totalTokens,
            @Nullable BigDecimal points,
            @Nullable String billingSource,
            String billingStatus,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            @Nullable String usageSource,
            @Nullable String upstreamRequestId) {

        public ChatCompletion {
            requireText(requestId, "requestId");
            requireText(status, "status");
            requireText(billingStatus, "billingStatus");
        }

        private static void requireText(String value, String name) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(name + " 不能为空");
            }
        }
    }

    public record ChatRequest(
            long id,
            String requestId,
            String spaceId,
            String userId,
            String agentSource,
            String teamId,
            String agentId,
            @Nullable String sessionKey,
            @Nullable Long conversationId,
            String idempotencyKey,
            @Nullable String modeCode,
            @Nullable String routeGroup,
            @Nullable String selectedProvider,
            @Nullable String selectedAccount,
            @Nullable String selectedModel,
            @Nullable String tier,
            String status,
            @Nullable String errorCode,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable Integer totalTokens,
            @Nullable BigDecimal points,
            BigDecimal overdraft,
            @Nullable String billingSource,
            String billingStatus,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            @Nullable String usageSource,
            @Nullable String upstreamRequestId,
            Instant createdAt,
            @Nullable Instant completedAt) {}
}
