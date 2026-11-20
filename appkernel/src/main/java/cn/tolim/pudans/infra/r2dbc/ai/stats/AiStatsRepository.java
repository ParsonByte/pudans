package cn.tolim.pudans.infra.r2dbc.ai.stats;

import static cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRowValues.count;
import static cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRowValues.decimal;

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
public class AiStatsRepository {

    private final DatabaseClient databaseClient;

    public Flux<RoutingStatsRow> routing(int days) {
        return databaseClient.sql("""
                        SELECT COALESCE(selected_provider, 'unknown') AS provider,
                               COALESCE(selected_model, 'unknown') AS model,
                               status,
                               COUNT(*) AS requests,
                               COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units
                        FROM ai_chat_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        GROUP BY selected_provider, selected_model, status
                        ORDER BY requests DESC
                        """)
                .bind("days", days)
                .map((row, metadata) -> new RoutingStatsRow(
                        row.get("provider", String.class),
                        row.get("model", String.class),
                        row.get("status", String.class),
                        count(row, "requests"),
                        decimal(row, "units")))
                .all();
    }

    public Flux<PointStatsRow> units(int days) {
        return databaseClient.sql("""
                        SELECT to_char(date_trunc('day', created_at AT TIME ZONE 'Asia/Shanghai'), 'YYYY-MM-DD') AS day,
                               COALESCE(selected_model, 'unknown') AS model,
                               COUNT(*) AS requests,
                               COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units,
                               COALESCE(SUM(total_tokens), 0) AS tokens
                        FROM ai_chat_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        GROUP BY day, selected_model
                        ORDER BY day DESC, units DESC
                        """)
                .bind("days", days)
                .map((row, metadata) -> new PointStatsRow(
                        row.get("day", String.class),
                        row.get("model", String.class),
                        count(row, "requests"),
                        decimal(row, "units"),
                        row.get("tokens", Long.class)))
                .all();
    }

    public Mono<RoutingSummaryRow> routingSummary(int days) {
        return databaseClient.sql("""
                        SELECT COUNT(*) AS requests,
                               COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                               COUNT(*) FILTER (WHERE status = 'RUNNING') AS running,
                               COALESCE(SUM(input_tokens), 0) AS input_tokens,
                               COALESCE(SUM(cache_tokens), 0) AS cache_tokens,
                               COALESCE(SUM(output_tokens), 0) AS output_tokens,
                               COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units,
                               COUNT(*) FILTER (WHERE created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')) AT TIME ZONE 'Asia/Shanghai') AS today_requests,
                               COUNT(*) FILTER (
                                   WHERE status = 'FAILED'
                                     AND created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')) AT TIME ZONE 'Asia/Shanghai'
                               ) AS today_failed,
                               COALESCE(SUM(total_tokens) FILTER (
                                   WHERE created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')) AT TIME ZONE 'Asia/Shanghai'
                               ), 0) AS today_tokens,
                               COALESCE(SUM(COALESCE(usage_units, points, 0)) FILTER (
                                   WHERE created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')) AT TIME ZONE 'Asia/Shanghai'
                               ), 0) AS today_units
                        FROM ai_chat_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        """)
                .bind("days", days)
                .map((row, metadata) -> new RoutingSummaryRow(
                        count(row, "requests"),
                        count(row, "failed"),
                        count(row, "running"),
                        count(row, "input_tokens"),
                        count(row, "cache_tokens"),
                        count(row, "output_tokens"),
                        decimal(row, "units"),
                        count(row, "today_requests"),
                        count(row, "today_failed"),
                        count(row, "today_tokens"),
                        decimal(row, "today_units")))
                .one();
    }

    public Flux<DailyRoutingRow> routingDays(int days) {
        return databaseClient.sql("""
                        SELECT to_char(date_trunc('day', created_at AT TIME ZONE 'Asia/Shanghai'), 'YYYY-MM-DD') AS day,
                               COUNT(*) AS requests,
                               COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                               COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units
                        FROM ai_chat_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        GROUP BY day
                        ORDER BY day DESC
                        """)
                .bind("days", days)
                .map((row, metadata) -> new DailyRoutingRow(
                        row.get("day", String.class),
                        count(row, "requests"),
                        count(row, "failed"),
                        decimal(row, "units")))
                .all();
    }

    // 供应商账号累计用量单位，用于 spendCapPoints 上限判断
    public Mono<BigDecimal> providerAccountSpent(String provider, String account) {
        return databaseClient.sql("""
                        SELECT COALESCE((
                                   SELECT SUM(COALESCE(usage_units, points, 0)) FROM ai_chat_request
                                   WHERE selected_provider = :provider AND selected_account = :account
                                     AND status = 'SUCCEEDED'
                               ), 0)
                             + COALESCE((
                                   SELECT SUM(COALESCE(usage_units, points, 0)) FROM ai_model_request
                                   WHERE selected_provider = :provider AND selected_account = :account
                                     AND status = 'SUCCEEDED'
                               ), 0) AS spent
                        """)
                .bind("provider", provider)
                .bind("account", account)
                .map((row, metadata) -> decimal(row, "spent"))
                .one()
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    // 团队在窗口内已结算的标准 token 单位，用于团队配额判断
    public Mono<BigDecimal> teamUnitsUsed(String teamId, Instant since) {
        return databaseClient.sql("""
                        SELECT COALESCE(SUM(units), 0) AS used
                        FROM ai_usage_ledger
                        WHERE team_id = :teamId AND status = 'SETTLED' AND created_at >= :since
                        """)
                .bind("teamId", teamId)
                .bind("since", OffsetDateTime.ofInstant(since, ZoneOffset.UTC))
                .map((row, metadata) -> decimal(row, "used"))
                .one()
                .defaultIfEmpty(BigDecimal.ZERO);
    }

    // 路由候选的实时性能输入：近窗口内成功请求的平均首字延迟、吞吐和输出量
    public Flux<RoutePerformanceRow> recentRoutePerformance(Instant since) {
        return databaseClient.sql("""
                        SELECT selected_provider AS provider,
                               selected_account AS account,
                               selected_model AS model,
                               AVG(ttft_ms) AS avg_ttft_ms,
                               AVG(tps) AS avg_tps,
                               COALESCE(SUM(output_tokens), 0) AS tokens
                        FROM ai_chat_request
                        WHERE created_at >= :since
                          AND status = 'SUCCEEDED'
                          AND selected_provider IS NOT NULL
                          AND selected_account IS NOT NULL
                          AND selected_model IS NOT NULL
                        GROUP BY selected_provider, selected_account, selected_model
                        """)
                .bind("since", OffsetDateTime.ofInstant(since, ZoneOffset.UTC))
                .map((row, metadata) -> new RoutePerformanceRow(
                        row.get("provider", String.class),
                        row.get("account", String.class),
                        row.get("model", String.class),
                        decimal(row, "avg_ttft_ms"),
                        decimal(row, "avg_tps"),
                        count(row, "tokens")))
                .all();
    }

    public Flux<RecentChatRequestRow> recentChatRequests(int days, int limit) {
        return databaseClient.sql("""
                        SELECT request_id, user_id, agent_source, route_group,
                               selected_provider, selected_account, selected_model, tier,
                               status, error_code, input_tokens, cache_tokens, output_tokens,
                               total_tokens, COALESCE(usage_units, points)::numeric AS units,
                               latency_ms, ttft_ms, tps,
                               session_key, conversation_id, created_at, completed_at
                        FROM ai_chat_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        ORDER BY created_at DESC
                        LIMIT :limit
                        """)
                .bind("days", days)
                .bind("limit", limit)
                .map((row, metadata) -> new RecentChatRequestRow(
                        row.get("request_id", String.class),
                        row.get("user_id", String.class),
                        row.get("agent_source", String.class),
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
                        row.get("units", BigDecimal.class),
                        row.get("latency_ms", Integer.class),
                        row.get("ttft_ms", Integer.class),
                        row.get("tps", BigDecimal.class),
                        row.get("session_key", String.class),
                        row.get("conversation_id", Long.class),
                        row.get("created_at", Instant.class),
                        row.get("completed_at", Instant.class)))
                .all();
    }

    public Flux<RoutingHistoryRow> routingHistory(
            Instant fromAt,
            Instant toAt,
            String status,
            String provider,
            String model,
            String agent,
            String group,
            boolean all,
            String userId,
            long beforeId,
            int limit) {
        return databaseClient.sql("""
                        SELECT r.id,
                               r.request_id,
                               r.conversation_id,
                               r.session_key,
                               c.title AS session_title,
                               r.agent_id,
                               r.mode_code,
                               r.route_group,
                               r.selected_provider,
                               r.selected_account,
                               r.selected_model,
                               r.tier,
                               r.status,
                               CASE WHEN r.route_trace ->> 'tries' ~ '^[0-9]+$'
                                    THEN (r.route_trace ->> 'tries')::int
                                    ELSE 0 END AS tries,
                               r.latency_ms,
                               r.created_at
                        FROM ai_chat_request r
                        LEFT JOIN ai_conversation c ON c.id = r.conversation_id
                        WHERE r.created_at >= :fromAt
                          AND r.created_at < :toAt
                          AND (:all OR r.user_id = :userId)
                          AND (:status = '' OR r.status = :status)
                          AND (:provider = '' OR r.selected_provider = :provider)
                          AND (:model = '' OR r.selected_model = :model)
                          AND (:agent = '' OR r.agent_id = :agent)
                          AND (:group = '' OR r.route_group = :group)
                          AND r.id < :beforeId
                        ORDER BY r.id DESC
                        LIMIT :limit
                        """)
                .bind("fromAt", OffsetDateTime.ofInstant(fromAt, ZoneOffset.UTC))
                .bind("toAt", OffsetDateTime.ofInstant(toAt, ZoneOffset.UTC))
                .bind("all", all)
                .bind("userId", userId)
                .bind("status", status)
                .bind("provider", provider)
                .bind("model", model)
                .bind("agent", agent)
                .bind("group", group)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> mapRoutingHistory(row))
                .all();
    }

    static RoutingHistoryRow mapRoutingHistory(Row row) {
        return new RoutingHistoryRow(
                row.get("id", Long.class),
                row.get("request_id", String.class),
                row.get("conversation_id", Long.class),
                row.get("session_key", String.class),
                row.get("session_title", String.class),
                row.get("agent_id", String.class),
                row.get("mode_code", String.class),
                row.get("route_group", String.class),
                row.get("selected_provider", String.class),
                row.get("selected_account", String.class),
                row.get("selected_model", String.class),
                row.get("tier", String.class),
                row.get("status", String.class),
                count(row, "tries"),
                row.get("latency_ms", Integer.class),
                row.get("created_at", Instant.class));
    }

    public Flux<AccountActivityRow> accountActivityToday() {
        return databaseClient.sql("""
                        SELECT selected_provider AS provider,
                               selected_account AS account,
                               COUNT(*) AS requests,
                               COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS success,
                               COUNT(*) FILTER (WHERE status = 'FAILED') AS failed
                        FROM ai_chat_request
                        WHERE created_at >= (date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai')) AT TIME ZONE 'Asia/Shanghai'
                          AND selected_provider IS NOT NULL
                          AND selected_account IS NOT NULL
                        GROUP BY selected_provider, selected_account
                        ORDER BY requests DESC
                        """)
                .map((row, metadata) -> new AccountActivityRow(
                        row.get("provider", String.class),
                        row.get("account", String.class),
                        count(row, "requests"),
                        count(row, "success"),
                        count(row, "failed")))
                .all();
    }

    public Flux<UserActivityRow> userActivity(int days, int limit) {
        return databaseClient.sql("""
                        SELECT user_id,
                               COUNT(*) AS requests,
                               COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                               MAX(created_at) AS last_request_at
                        FROM ai_chat_request
                        WHERE created_at >= now() - make_interval(days => :days)
                        GROUP BY user_id
                        ORDER BY requests DESC, last_request_at DESC
                        LIMIT :limit
                        """)
                .bind("days", days)
                .bind("limit", limit)
                .map((row, metadata) -> new UserActivityRow(
                        row.get("user_id", String.class),
                        count(row, "requests"),
                        count(row, "failed"),
                        row.get("last_request_at", Instant.class)))
                .all();
    }

    public Mono<MemoryStatsRow> memory() {
        return databaseClient.sql("""
                        SELECT
                            (SELECT COUNT(*) FROM ai_memory_l1) AS l1_total,
                            (SELECT COUNT(*) FROM ai_memory_l1 WHERE status = 'ACTIVE') AS l1_active,
                            (SELECT COUNT(*) FROM ai_memory_l2) AS l2_total,
                            (SELECT COUNT(*) FROM ai_memory_l3) AS l3_total
                        """)
                .map((row, metadata) -> new MemoryStatsRow(
                        count(row, "l1_total"),
                        count(row, "l1_active"),
                        count(row, "l2_total"),
                        count(row, "l3_total")))
                .one();
    }

    public Mono<KnowledgeBaseStatsRow> knowledgeBase() {
        return databaseClient.sql("""
                        SELECT
                            (SELECT COUNT(*) FROM ai_kb_document) AS documents,
                            (SELECT COUNT(*) FROM ai_kb_document WHERE status = 'READY') AS ready,
                            (SELECT COUNT(*) FROM ai_kb_document WHERE status = 'FAILED') AS failed,
                            (SELECT COUNT(*) FROM ai_kb_chunk) AS chunks,
                            (SELECT COUNT(*) FROM ai_kb_query_log) AS queries
                        """)
                .map((row, metadata) -> new KnowledgeBaseStatsRow(
                        count(row, "documents"),
                        count(row, "ready"),
                        count(row, "failed"),
                        count(row, "chunks"),
                        count(row, "queries")))
                .one();
    }

    public record RoutePerformanceRow(
            String provider,
            String account,
            String model,
            @Nullable BigDecimal avgTtftMillis,
            @Nullable BigDecimal avgTps,
            long tokens) {}

    public record RoutingStatsRow(
            String provider,
            String model,
            String status,
            long requests,
            BigDecimal units) {}

    public record PointStatsRow(
            String day,
            String model,
            long requests,
            BigDecimal units,
            long tokens) {}

    public record RoutingSummaryRow(
            long requests,
            long failed,
            long running,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            BigDecimal units,
            long todayRequests,
            long todayFailed,
            long todayTokens,
            BigDecimal todayUnits) {}

    public record DailyRoutingRow(
            String day,
            long requests,
            long failed,
            BigDecimal units) {}

    public record RecentChatRequestRow(
            String requestId,
            String userId,
            String agentSource,
            @Nullable String routeGroup,
            @Nullable String provider,
            @Nullable String account,
            @Nullable String model,
            @Nullable String tier,
            String status,
            @Nullable String errorCode,
            @Nullable Integer inputTokens,
            @Nullable Integer cacheTokens,
            @Nullable Integer outputTokens,
            @Nullable Integer totalTokens,
            @Nullable BigDecimal units,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            @Nullable String sessionKey,
            @Nullable Long conversationId,
            Instant createdAt,
            @Nullable Instant completedAt) {}

    public record RoutingHistoryRow(
            long id,
            String requestId,
            @Nullable Long conversationId,
            @Nullable String sessionKey,
            @Nullable String sessionTitle,
            String agent,
            @Nullable String mode,
            @Nullable String routeGroup,
            @Nullable String provider,
            @Nullable String account,
            @Nullable String model,
            @Nullable String tier,
            String status,
            long tries,
            @Nullable Integer latencyMs,
            Instant createdAt) {}

    public record AccountActivityRow(
            String provider, String account, long requests, long success, long failed) {}

    public record UserActivityRow(
            String userId, long requests, long failed, @Nullable Instant lastRequestAt) {}

    public record MemoryStatsRow(long l1Total, long l1Active, long l2Total, long l3Total) {}

    public record KnowledgeBaseStatsRow(
            long documents, long ready, long failed, long chunks, long queries) {}
}
