package cn.tolim.pudans.infra.r2dbc.ai.chat;

import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
                            selected_provider, selected_account, selected_model, tier,
                            route_trace, caller_key, plan_id, billing_scope
                        ) VALUES (
                            :requestId, :spaceId, :userId, :agentSource, :teamId, :agentId, :sessionKey,
                            :conversationId, :idempotencyKey, :modeCode, :routeGroup,
                            :selectedProvider, :selectedAccount, :selectedModel, :tier,
                            CAST(:routeTrace AS jsonb), :callerKey, :planId, :billingScope
                        )
                        RETURNING id, request_id, space_id, user_id, agent_source, team_id, agent_id, session_key,
                                  conversation_id, idempotency_key, mode_code, route_group,
                                  selected_provider, selected_account, selected_model, tier, status,
                                  error_code, input_tokens, cache_tokens, output_tokens, total_tokens,
                                  usage_units, plan_id, billing_scope, billing_source, billing_status,
                                  latency_ms, ttft_ms, tps, usage_source, upstream_request_id,
                                  route_trace::text AS route_trace, attempts::text AS attempts, caller_key,
                                  created_at, completed_at
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
        spec = AiR2dbcBindings.bindNullable(spec, "routeTrace", request.routeTrace(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "callerKey", request.callerKey(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "planId", request.planId(), String.class);
        spec = spec.bind("billingScope", request.billingScope());
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
                            usage_units = :usageUnits,
                            billing_source = :billingSource,
                            billing_status = :billingStatus,
                            latency_ms = :latencyMs,
                            ttft_ms = :ttftMs,
                            tps = :tps,
                            usage_source = :usageSource,
                            upstream_request_id = :upstreamRequestId,
                            route_trace = COALESCE(CAST(:routeTrace AS jsonb), route_trace),
                            attempts = COALESCE(CAST(:attempts AS jsonb), attempts),
                            completed_at = now()
                        WHERE request_id = :requestId
                        RETURNING id, request_id, space_id, user_id, agent_source, team_id, agent_id, session_key,
                                  conversation_id, idempotency_key, mode_code, route_group,
                                  selected_provider, selected_account, selected_model, tier, status,
                                  error_code, input_tokens, cache_tokens, output_tokens, total_tokens,
                                  usage_units, plan_id, billing_scope, billing_source, billing_status,
                                  latency_ms, ttft_ms, tps, usage_source, upstream_request_id,
                                  route_trace::text AS route_trace, attempts::text AS attempts, caller_key,
                                  created_at, completed_at
                        """)
                .bind("status", completion.status())
                .bind("billingStatus", completion.billingStatus())
                .bind("requestId", completion.requestId());
        spec = AiR2dbcBindings.bindNullable(spec, "errorCode", completion.errorCode(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "inputTokens", completion.inputTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "cacheTokens", completion.cacheTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "outputTokens", completion.outputTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "totalTokens", completion.totalTokens(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "usageUnits", completion.units(), Long.class);
        spec = AiR2dbcBindings.bindNullable(spec, "billingSource", completion.billingSource(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "latencyMs", completion.latencyMs(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "ttftMs", completion.ttftMs(), Integer.class);
        spec = AiR2dbcBindings.bindNullable(spec, "tps", completion.tps(), BigDecimal.class);
        spec = AiR2dbcBindings.bindNullable(spec, "usageSource", completion.usageSource(), String.class);
        spec = AiR2dbcBindings.bindNullable(
                spec, "upstreamRequestId", completion.upstreamRequestId(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "routeTrace", completion.routeTrace(), String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "attempts", completion.attempts(), String.class);
        return spec.map((row, metadata) -> mapChatRequest(row)).one();
    }

    public Mono<KeyUsage> aggregateKeyUsage(
            @Nullable String userId,
            String callerKey,
            @Nullable Instant since,
            @Nullable List<String> models,
            @Nullable String account) {
        var sql = new StringBuilder("""
                        SELECT COUNT(*) AS requests,
                               COALESCE(SUM(total_tokens), 0) AS tokens,
                               COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units
                        FROM ai_chat_request
                        WHERE caller_key = :callerKey
                        """);
        if (userId != null && !userId.isBlank()) {
            sql.append(" AND user_id = :userId");
        }
        if (since != null) {
            sql.append(" AND created_at >= :since");
        }
        if (models != null && !models.isEmpty()) {
            sql.append(" AND selected_model IN (:models)");
        }
        if (account != null && !account.isBlank()) {
            sql.append(" AND selected_account = :account");
        }
        var spec = databaseClient.sql(sql.toString()).bind("callerKey", callerKey);
        spec = AiR2dbcBindings.bindNullable(spec, "userId", userId, String.class);
        spec = AiR2dbcBindings.bindNullable(spec, "since", since, Instant.class);
        if (models != null && !models.isEmpty()) {
            spec = spec.bind("models", models);
        }
        spec = AiR2dbcBindings.bindNullable(spec, "account", account, String.class);
        return spec.map((row, metadata) -> new KeyUsage(
                        row.get("requests", Long.class),
                        row.get("tokens", Long.class),
                        row.get("units", BigDecimal.class)))
                .one();
    }

    public Mono<Long> countRecentFailures(Instant since) {
        return databaseClient
                .sql("""
                        SELECT COUNT(*) AS failures
                        FROM ai_chat_request
                        WHERE status = 'FAILED' AND created_at >= :since
                        """)
                .bind("since", since)
                .map((row, metadata) -> row.get("failures", Long.class))
                .one();
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
                row.get("usage_units", Long.class),
                row.get("plan_id", String.class),
                row.get("billing_scope", String.class),
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
            @Nullable String tier,
            @Nullable String routeTrace,
            @Nullable String callerKey,
            @Nullable String planId,
            String billingScope) {

        public NewChatRequest {
            requireText(requestId, "requestId");
            requireText(spaceId, "spaceId");
            requireText(userId, "userId");
            agentSource = agentSource == null || agentSource.isBlank() ? "pudans-chat" : agentSource;
            requireText(teamId, "teamId");
            requireText(agentId, "agentId");
            requireText(idempotencyKey, "idempotencyKey");
            requireText(billingScope, "billingScope");
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
            @Nullable Long units,
            @Nullable String billingSource,
            String billingStatus,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            @Nullable String usageSource,
            @Nullable String upstreamRequestId,
            @Nullable String routeTrace,
            @Nullable String attempts) {

        public ChatCompletion(
                String requestId,
                String status,
                @Nullable String errorCode,
                @Nullable Integer inputTokens,
                @Nullable Integer cacheTokens,
                @Nullable Integer outputTokens,
                @Nullable Integer totalTokens,
                @Nullable Long units,
                @Nullable String billingSource,
                String billingStatus,
                @Nullable Integer latencyMs,
                @Nullable Integer ttftMs,
                @Nullable BigDecimal tps,
                @Nullable String usageSource,
                @Nullable String upstreamRequestId) {
            this(
                    requestId,
                    status,
                    errorCode,
                    inputTokens,
                    cacheTokens,
                    outputTokens,
                    totalTokens,
                    units,
                    billingSource,
                    billingStatus,
                    latencyMs,
                    ttftMs,
                    tps,
                    usageSource,
                    upstreamRequestId,
                    null,
                    null);
        }

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

    public record KeyUsage(long requests, long tokens, BigDecimal units) {}

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
            @Nullable Long units,
            @Nullable String planId,
            String billingScope,
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
