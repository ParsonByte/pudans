package cn.tolim.pudans.infra.r2dbc.ai.stats;

import static cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRowValues.count;
import static cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRowValues.decimal;

import cn.tolim.pudans.infra.api.utils.JsonUtils;
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
public class AiUsageRepository {

    private final DatabaseClient databaseClient;

    public Flux<OverviewDailyRow> overviewSeries(
            Instant fromAt,
            Instant toAt,
            String team,
            String agent,
            String provider,
            String model,
            String bucket,
            boolean all,
            String userId) {
        return databaseClient.sql("""
                        WITH usage AS (
                            SELECT created_at, status, input_tokens, cache_tokens, output_tokens,
                                   total_tokens, COALESCE(usage_units, points) AS units
                            FROM ai_chat_request
                            WHERE created_at >= :fromAt
                              AND created_at < :toAt
                              AND (:all OR user_id = :userId)
                              AND (:team = '' OR team_id = :team)
                              AND (:agent = '' OR agent_id = :agent)
                              AND (:provider = '' OR selected_provider = :provider)
                              AND (:model = '' OR selected_model = :model)
                            UNION ALL
                            SELECT created_at, status, input_tokens, cache_tokens, output_tokens,
                                   COALESCE(input_tokens, 0) + COALESCE(cache_tokens, 0)
                                       + COALESCE(output_tokens, 0) AS total_tokens,
                                   COALESCE(usage_units, points) AS units
                            FROM ai_model_request
                            WHERE created_at >= :fromAt
                              AND created_at < :toAt
                              AND (:all OR user_id = :userId)
                              AND (:team = '' OR team_id = :team)
                              AND (:agent = '' OR agent_id = :agent)
                              AND (:provider = '' OR selected_provider = :provider)
                              AND (:model = '' OR selected_model = :model)
                        )
                        SELECT CASE :bucket
                                   WHEN 'hour' THEN to_char(
                                       date_trunc('hour', created_at AT TIME ZONE 'Asia/Shanghai'),
                                       'YYYY-MM-DD"T"HH24:00')
                                   WHEN 'week' THEN to_char(
                                       date_trunc('week', created_at AT TIME ZONE 'Asia/Shanghai'),
                                       'YYYY-MM-DD')
                                   ELSE to_char(
                                       date_trunc('day', created_at AT TIME ZONE 'Asia/Shanghai'),
                                       'YYYY-MM-DD')
                               END AS day,
                               COUNT(*) AS requests,
                               COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS succeeded,
                               COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                               COALESCE(SUM(input_tokens), 0) AS input_tokens,
                               COALESCE(SUM(cache_tokens), 0) AS cache_tokens,
                               COALESCE(SUM(output_tokens), 0) AS output_tokens,
                               COALESCE(SUM(total_tokens), 0) AS total_tokens,
                               COALESCE(SUM(units), 0) AS units
                        FROM usage
                        GROUP BY day
                        ORDER BY day
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("bucket", bucket)
                .bind("all", all)
                .bind("userId", userId)
                .bind("team", team)
                .bind("agent", agent)
                .bind("provider", provider)
                .bind("model", model)
                .map((row, metadata) -> mapDaily(row))
                .all();
    }

    public Flux<OverviewRankingRow> overviewRankings(
            Instant fromAt,
            Instant toAt,
            String team,
            String agent,
            String provider,
            String model,
            boolean all,
            String userId) {
        return databaseClient.sql("""
                        WITH usage AS (
                            SELECT created_at, agent_id, selected_model, selected_provider, selected_account,
                                   status, input_tokens, cache_tokens, output_tokens, total_tokens,
                                   COALESCE(usage_units, points) AS units,
                                   latency_ms, ttft_ms, tps, route_trace
                            FROM ai_chat_request
                            WHERE created_at >= :fromAt
                              AND created_at < :toAt
                              AND (:all OR user_id = :userId)
                              AND (:team = '' OR team_id = :team)
                              AND (:agent = '' OR agent_id = :agent)
                              AND (:provider = '' OR selected_provider = :provider)
                              AND (:model = '' OR selected_model = :model)
                            UNION ALL
                            SELECT created_at, agent_id, selected_model, selected_provider, selected_account,
                                   status, input_tokens, cache_tokens, output_tokens,
                                   COALESCE(input_tokens, 0) + COALESCE(cache_tokens, 0)
                                       + COALESCE(output_tokens, 0) AS total_tokens,
                                   COALESCE(usage_units, points) AS units,
                                   latency_ms, NULL AS ttft_ms, NULL AS tps, route_trace
                            FROM ai_model_request
                            WHERE created_at >= :fromAt
                              AND created_at < :toAt
                              AND (:all OR user_id = :userId)
                              AND (:team = '' OR team_id = :team)
                              AND (:agent = '' OR agent_id = :agent)
                              AND (:provider = '' OR selected_provider = :provider)
                              AND (:model = '' OR selected_model = :model)
                        )
                        SELECT COALESCE(agent_id, 'unknown') AS agent,
                               COALESCE(selected_model, 'unknown') AS model,
                               COALESCE(selected_provider, 'unknown') AS provider,
                               COALESCE(selected_account, 'unknown') AS account,
                               COUNT(*) AS requests,
                               COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS succeeded,
                               COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                               COUNT(*) FILTER (WHERE status IN ('CANCELED', 'CANCELLED')) AS canceled_requests,
                               COUNT(*) FILTER (WHERE units IS NULL) AS unpriced_requests,
                               COUNT(*) FILTER (
                                   WHERE CASE WHEN route_trace ->> 'tries' ~ '^[0-9]+$'
                                              THEN (route_trace ->> 'tries')::int > 1
                                              ELSE FALSE END
                               ) AS retried_requests,
                               COALESCE(SUM(CASE WHEN route_trace ->> 'reasoningTokens' ~ '^[0-9]+$'
                                                 THEN (route_trace ->> 'reasoningTokens')::bigint
                                                 ELSE 0 END), 0) AS reasoning_tokens,
                               COALESCE(SUM(input_tokens), 0) AS input_tokens,
                               COALESCE(SUM(cache_tokens), 0) AS cache_tokens,
                               COALESCE(SUM(output_tokens), 0) AS output_tokens,
                               COALESCE(SUM(total_tokens), 0) AS total_tokens,
                               COALESCE(SUM(units), 0) AS units,
                               COALESCE(SUM(latency_ms), 0) AS latency_sum,
                               COUNT(latency_ms) AS latency_count,
                               COALESCE(SUM(ttft_ms), 0) AS ttft_sum,
                               COUNT(ttft_ms) AS ttft_count,
                               COALESCE(SUM(tps), 0) AS tps_sum,
                               COUNT(tps) AS tps_count
                        FROM usage
                        GROUP BY agent_id, selected_model, selected_provider, selected_account
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("team", team)
                .bind("agent", agent)
                .bind("provider", provider)
                .bind("model", model)
                .map((row, metadata) -> mapRanking(row))
                .all();
    }

    public Mono<RequestTotalsRow> requestTotals(
            Instant fromAt,
            Instant toAt,
            String status,
            String provider,
            String model,
            String account,
            String agent,
            String agentSource,
            String mode,
            String routeGroup,
            String q,
            String purpose,
            String via,
            String callerKey,
            boolean retried,
            boolean all,
            String userId) {
        return requestQuery(
                        """
                        SELECT COUNT(*) AS requests,
                               COUNT(*) FILTER (WHERE status = 'SUCCEEDED') AS succeeded,
                               COUNT(*) FILTER (WHERE status = 'FAILED') AS failed,
                               COUNT(*) FILTER (WHERE status IN ('CANCELED', 'CANCELLED')) AS canceled_requests,
                               COUNT(*) FILTER (WHERE COALESCE(usage_units, points) IS NULL) AS unpriced_requests,
                               COUNT(*) FILTER (
                                   WHERE CASE WHEN route_trace ->> 'tries' ~ '^[0-9]+$'
                                              THEN (route_trace ->> 'tries')::int > 1
                                              ELSE FALSE END
                               ) AS retried_requests,
                               COALESCE(SUM(CASE WHEN route_trace ->> 'reasoningTokens' ~ '^[0-9]+$'
                                                 THEN (route_trace ->> 'reasoningTokens')::bigint
                                                 ELSE 0 END), 0) AS reasoning_tokens,
                               COALESCE(SUM(input_tokens), 0) AS input_tokens,
                               COALESCE(SUM(cache_tokens), 0) AS cache_tokens,
                               COALESCE(SUM(output_tokens), 0) AS output_tokens,
                               COALESCE(SUM(total_tokens), 0) AS total_tokens,
                               COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units
                        FROM ai_chat_request
                        WHERE created_at >= :fromAt
                          AND created_at < :toAt
                          AND (:all OR user_id = :userId)
                          AND (:status = '' OR status = :status)
                          AND (:provider = '' OR selected_provider = :provider)
                          AND (:model = '' OR selected_model = :model)
                          AND (:account = '' OR selected_account = :account)
                          AND (:agent = '' OR agent_id = :agent)
                          AND (:agentSource = '' OR agent_source = :agentSource)
                          AND (:mode = '' OR mode_code = :mode)
                          AND (:routeGroup = '' OR route_group = :routeGroup)
                          AND (:q = '' OR request_id ILIKE :qPattern OR session_key ILIKE :qPattern)
                          AND (:purpose = '' OR route_trace ->> 'purpose' = :purpose)
                          AND (:via = '' OR route_trace ->> 'via' = :via)
                          AND (:callerKey = '' OR route_trace ->> 'callerKey' = :callerKey)
                          AND (:retried = FALSE OR CASE WHEN route_trace ->> 'tries' ~ '^[0-9]+$'
                                                        THEN (route_trace ->> 'tries')::int > 1
                                                        ELSE FALSE END)
                        """,
                        fromAt,
                        toAt,
                        status,
                        provider,
                        model,
                        account,
                        agent,
                        agentSource,
                        mode,
                        routeGroup,
                        q,
                        purpose,
                        via,
                        callerKey,
                        retried,
                        all,
                        userId)
                .map((row, metadata) -> mapRequestTotals(row))
                .one();
    }

    public Flux<RequestRow> requests(
            Instant fromAt,
            Instant toAt,
            String status,
            String provider,
            String model,
            String account,
            String agent,
            String agentSource,
            String mode,
            String routeGroup,
            String q,
            String purpose,
            String via,
            String callerKey,
            boolean retried,
            boolean all,
            String userId,
            long beforeId,
            int limit) {
        return requestQuery(
                        """
                        SELECT id, request_id, user_id, team_id, agent_id, agent_source, mode_code,
                               route_group, selected_provider, selected_account, selected_model, tier,
                               status, error_code, input_tokens, cache_tokens, output_tokens, total_tokens,
                               COALESCE(usage_units, points) AS units,
                               latency_ms, ttft_ms, tps, session_key, conversation_id,
                               CASE WHEN route_trace ->> 'tries' ~ '^[0-9]+$'
                                    THEN (route_trace ->> 'tries')::int > 1
                                    ELSE FALSE END AS retried,
                               COALESCE(route_trace ->> 'servedEffort', route_trace ->> 'effort')
                                   AS served_effort,
                               created_at, completed_at
                        FROM ai_chat_request
                        WHERE created_at >= :fromAt
                          AND created_at < :toAt
                          AND (:all OR user_id = :userId)
                          AND (:status = '' OR status = :status)
                          AND (:provider = '' OR selected_provider = :provider)
                          AND (:model = '' OR selected_model = :model)
                          AND (:account = '' OR selected_account = :account)
                          AND (:agent = '' OR agent_id = :agent)
                          AND (:agentSource = '' OR agent_source = :agentSource)
                          AND (:mode = '' OR mode_code = :mode)
                          AND (:routeGroup = '' OR route_group = :routeGroup)
                          AND (:q = '' OR request_id ILIKE :qPattern OR session_key ILIKE :qPattern)
                          AND (:purpose = '' OR route_trace ->> 'purpose' = :purpose)
                          AND (:via = '' OR route_trace ->> 'via' = :via)
                          AND (:callerKey = '' OR route_trace ->> 'callerKey' = :callerKey)
                          AND (:retried = FALSE OR CASE WHEN route_trace ->> 'tries' ~ '^[0-9]+$'
                                                        THEN (route_trace ->> 'tries')::int > 1
                                                        ELSE FALSE END)
                          AND id < :beforeId
                        ORDER BY id DESC
                        LIMIT :limit
                        """,
                        fromAt,
                        toAt,
                        status,
                        provider,
                        model,
                        account,
                        agent,
                        agentSource,
                        mode,
                        routeGroup,
                        q,
                        purpose,
                        via,
                        callerKey,
                        retried,
                        all,
                        userId)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> mapRequest(row))
                .all();
    }

    public Mono<RequestDetailRow> requestDetail(String requestId) {
        return databaseClient.sql("""
                        SELECT request_id, user_id, team_id, agent_id, agent_source, mode_code,
                               route_group, selected_provider, selected_account, selected_model, tier,
                               status, error_code, input_tokens, cache_tokens, output_tokens, total_tokens,
                               COALESCE(usage_units, points) AS units,
                               billing_source, billing_status, usage_source,
                               upstream_request_id, latency_ms, ttft_ms, tps, session_key, conversation_id,
                               route_trace::text AS route_trace,
                               retrieval_trace::text AS retrieval_trace,
                               created_at, completed_at
                        FROM ai_chat_request
                        WHERE request_id = :requestId
                        """)
                .bind("requestId", requestId)
                .map((row, metadata) -> mapRequestDetail(row))
                .one();
    }

    public Flux<SessionRow> sessions(
            Instant fromAt,
            Instant toAt,
            String agent,
            String q,
            String provider,
            String model,
            String status,
            String routeGroup,
            boolean all,
            String userId,
            long beforeId,
            int limit) {
        return databaseClient.sql("""
                        SELECT c.id AS conversation_id,
                               c.title,
                               c.user_id,
                               c.team_id,
                               c.agent_id,
                               c.agent_source,
                               c.status,
                               c.created_at,
                               c.last_activity_at,
                               COALESCE(r.request_count, 0) AS request_count,
                               COALESCE(r.input_tokens, 0) AS input_tokens,
                               COALESCE(r.cache_tokens, 0) AS cache_tokens,
                               COALESCE(r.output_tokens, 0) AS output_tokens,
                               COALESCE(r.total_tokens, 0) AS total_tokens,
                               COALESCE(r.units, 0) AS units,
                               r.model,
                               COALESCE(m.message_count, 0) AS message_count
                        FROM ai_conversation c
                        LEFT JOIN (
                            SELECT conversation_id,
                                   COUNT(*) AS request_count,
                                   COALESCE(SUM(input_tokens), 0) AS input_tokens,
                                   COALESCE(SUM(cache_tokens), 0) AS cache_tokens,
                                   COALESCE(SUM(output_tokens), 0) AS output_tokens,
                                   COALESCE(SUM(total_tokens), 0) AS total_tokens,
                                   COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units,
                                   (ARRAY_AGG(selected_model ORDER BY created_at DESC)
                                       FILTER (WHERE selected_model IS NOT NULL))[1] AS model
                            FROM ai_chat_request
                            WHERE conversation_id IS NOT NULL
                              AND created_at >= :fromAt
                              AND created_at < :toAt
                              AND (:provider = '' OR selected_provider = :provider)
                              AND (:model = '' OR selected_model = :model)
                              AND (:status = '' OR status = :status)
                              AND (:routeGroup = '' OR route_group = :routeGroup)
                            GROUP BY conversation_id
                        ) r ON r.conversation_id = c.id
                        LEFT JOIN (
                            SELECT conversation_id, COUNT(*) AS message_count
                            FROM ai_message
                            WHERE deleted_at IS NULL
                              AND created_at >= :fromAt
                              AND created_at < :toAt
                            GROUP BY conversation_id
                        ) m ON m.conversation_id = c.id
                        WHERE c.deleted_at IS NULL
                          AND c.last_activity_at >= :fromAt
                          AND c.last_activity_at < :toAt
                          AND (:all OR c.user_id = :userId)
                          AND (:agent = '' OR c.agent_id = :agent)
                          AND (:q = '' OR c.title ILIKE :qPattern OR c.session_key ILIKE :qPattern)
                          AND (:provider = '' AND :model = '' AND :status = '' AND :routeGroup = ''
                               OR r.request_count IS NOT NULL)
                          AND c.id < :beforeId
                        ORDER BY c.id DESC
                        LIMIT :limit
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("agent", agent)
                .bind("q", q)
                .bind("qPattern", pattern(q))
                .bind("provider", provider)
                .bind("model", model)
                .bind("status", status)
                .bind("routeGroup", routeGroup)
                .bind("beforeId", beforeId)
                .bind("limit", limit)
                .map((row, metadata) -> mapSession(row))
                .all();
    }

    public Mono<SessionUsageRow> sessionUsage(long conversationId) {
        return databaseClient.sql("""
                        SELECT
                            (SELECT COUNT(*) FROM ai_chat_request
                             WHERE conversation_id = :conversationId) AS request_count,
                            COALESCE((SELECT SUM(input_tokens) FROM ai_chat_request
                                      WHERE conversation_id = :conversationId), 0) AS input_tokens,
                            COALESCE((SELECT SUM(cache_tokens) FROM ai_chat_request
                                      WHERE conversation_id = :conversationId), 0) AS cache_tokens,
                            COALESCE((SELECT SUM(output_tokens) FROM ai_chat_request
                                      WHERE conversation_id = :conversationId), 0) AS output_tokens,
                            COALESCE((SELECT SUM(total_tokens) FROM ai_chat_request
                                      WHERE conversation_id = :conversationId), 0) AS total_tokens,
                            COALESCE((SELECT SUM(COALESCE(usage_units, points, 0)) FROM ai_chat_request
                                      WHERE conversation_id = :conversationId), 0) AS units,
                            (SELECT selected_model FROM ai_chat_request
                             WHERE conversation_id = :conversationId
                               AND selected_model IS NOT NULL
                             ORDER BY created_at DESC
                             LIMIT 1) AS model,
                            (SELECT COUNT(*) FROM ai_message
                             WHERE conversation_id = :conversationId
                               AND deleted_at IS NULL) AS message_count,
                            (SELECT COUNT(*) FROM ai_message
                             WHERE conversation_id = :conversationId
                               AND deleted_at IS NULL
                               AND role = 'user') AS user_message_count,
                            (SELECT COUNT(*) FROM ai_message
                             WHERE conversation_id = :conversationId
                               AND deleted_at IS NULL
                               AND role = 'assistant') AS assistant_message_count
                        """)
                .bind("conversationId", conversationId)
                .map((row, metadata) -> mapSessionUsage(row))
                .one();
    }

    public Flux<MessageRow> recentMessages(long conversationId, int limit) {
        return databaseClient.sql("""
                        SELECT id AS message_id,
                               role,
                               LEFT(COALESCE(content, ''), 200) AS content_preview,
                               token_count,
                               model_code,
                               created_at
                        FROM ai_message
                        WHERE conversation_id = :conversationId
                          AND deleted_at IS NULL
                        ORDER BY id DESC
                        LIMIT :limit
                        """)
                .bind("conversationId", conversationId)
                .bind("limit", limit)
                .map((row, metadata) -> mapMessage(row))
                .all();
    }

    public Flux<ContextRow> contextRows(
            Instant fromAt, Instant toAt, String agent, boolean all, String userId) {
        return databaseClient.sql("""
                        SELECT c.agent_id,
                               COALESCE(c.selected_model, 'unknown') AS model,
                               COUNT(*) AS requests,
                               COALESCE(SUM(c.input_tokens), 0) AS input_tokens,
                               COALESCE(SUM(c.output_tokens), 0) AS output_tokens,
                               COALESCE(SUM(CASE WHEN c.route_trace ->> 'memoryTokens' ~ '^[0-9]+$'
                                                 THEN (c.route_trace ->> 'memoryTokens')::bigint
                                                 ELSE 0 END), 0) AS memory_tokens,
                               COALESCE(SUM(CASE WHEN c.retrieval_trace ->> 'kbTokens' ~ '^[0-9]+$'
                                                 THEN (c.retrieval_trace ->> 'kbTokens')::bigint
                                                 ELSE 0 END), 0) AS kb_tokens,
                               COALESCE(SUM(msgs.user_chars), 0) AS user_chars,
                               COALESCE(SUM(msgs.user_cjk_chars), 0) AS user_cjk_chars
                        FROM ai_chat_request c
                        LEFT JOIN (
                            SELECT m.request_id,
                                   SUM(char_length(COALESCE(m.content, ''))) AS user_chars,
                                   SUM(char_length(regexp_replace(
                                       COALESCE(m.content, ''), '[^一-鿿㐀-䶿぀-ヿ가-힯]', '', 'g')))
                                       AS user_cjk_chars
                            FROM ai_message m
                            WHERE m.deleted_at IS NULL
                              AND m.role = 'user'
                              AND m.request_id IS NOT NULL
                              AND m.created_at >= :fromAt
                              AND m.created_at < :toAt
                            GROUP BY m.request_id
                        ) msgs ON msgs.request_id = c.request_id
                        WHERE c.created_at >= :fromAt
                          AND c.created_at < :toAt
                          AND (:all OR c.user_id = :userId)
                          AND (:agent = '' OR c.agent_id = :agent)
                        GROUP BY c.agent_id, c.selected_model
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("agent", agent)
                .map((row, metadata) -> mapContext(row))
                .all();
    }

    public Flux<SpeedRow> speed(
            Instant fromAt, Instant toAt, String groupBy, boolean all, String userId) {
        return databaseClient.sql("""
                        SELECT CASE WHEN :groupBy = 'provider'
                                    THEN COALESCE(selected_provider, 'unknown')
                                    ELSE COALESCE(selected_model, 'unknown')
                               END AS name,
                               COUNT(*) AS requests,
                               COUNT(*) FILTER (WHERE status = 'FAILED') AS failures,
                               AVG(ttft_ms)::double precision AS avg_ttft_ms,
                               percentile_cont(0.5) WITHIN GROUP (ORDER BY ttft_ms)
                                   ::double precision AS p50_ttft_ms,
                               AVG(tps)::double precision AS avg_tps,
                               percentile_cont(0.5) WITHIN GROUP (ORDER BY tps)
                                   ::double precision AS p50_tps
                        FROM ai_chat_request
                        WHERE created_at >= :fromAt
                          AND created_at < :toAt
                          AND (:all OR user_id = :userId)
                        GROUP BY 1
                        ORDER BY requests DESC, name
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("groupBy", groupBy)
                .bind("all", all)
                .bind("userId", userId)
                .map((row, metadata) -> mapSpeed(row))
                .all();
    }

    public Mono<SessionStatsTotalsRow> sessionStatsTotals(
            Instant fromAt,
            Instant toAt,
            String agent,
            String model,
            boolean all,
            String userId) {
        return databaseClient.sql("""
                        WITH scoped AS (
                            SELECT c.id
                            FROM ai_conversation c
                            WHERE c.deleted_at IS NULL
                              AND c.last_activity_at >= :fromAt
                              AND c.last_activity_at < :toAt
                              AND (:all OR c.user_id = :userId)
                              AND (:agent = '' OR c.agent_id = :agent)
                              AND (:model = '' OR EXISTS (
                                  SELECT 1 FROM ai_chat_request rm
                                  WHERE rm.conversation_id = c.id
                                    AND rm.created_at >= :fromAt
                                    AND rm.created_at < :toAt
                                    AND rm.selected_model = :model
                              ))
                        ),
                        requests AS (
                            SELECT r.conversation_id,
                                   COALESCE(SUM(r.total_tokens), 0) AS tokens,
                                   COALESCE(SUM(COALESCE(r.usage_units, r.points, 0)), 0) AS units,
                                   COALESCE(SUM(r.cache_tokens), 0) AS cache_tokens
                            FROM ai_chat_request r
                            JOIN scoped s ON s.id = r.conversation_id
                            WHERE r.created_at >= :fromAt
                              AND r.created_at < :toAt
                              AND (:model = '' OR r.selected_model = :model)
                            GROUP BY r.conversation_id
                        ),
                        messages AS (
                            SELECT m.conversation_id, COUNT(*) AS message_count
                            FROM ai_message m
                            JOIN scoped s ON s.id = m.conversation_id
                            WHERE m.deleted_at IS NULL
                              AND m.created_at >= :fromAt
                              AND m.created_at < :toAt
                            GROUP BY m.conversation_id
                        ),
                        events AS (
                            SELECT r.conversation_id, r.created_at
                            FROM ai_chat_request r
                            JOIN scoped s ON s.id = r.conversation_id
                            WHERE r.created_at >= :fromAt
                              AND r.created_at < :toAt
                              AND (:model = '' OR r.selected_model = :model)
                            UNION ALL
                            SELECT m.conversation_id, m.created_at
                            FROM ai_message m
                            JOIN scoped s ON s.id = m.conversation_id
                            WHERE m.deleted_at IS NULL
                              AND m.created_at >= :fromAt
                              AND m.created_at < :toAt
                        ),
                        active AS (
                            SELECT conversation_id,
                                   SUM(EXTRACT(EPOCH FROM gap)) FILTER (
                                       WHERE gap IS NOT NULL AND gap <= INTERVAL '5 minutes'
                                   ) AS active_seconds
                            FROM (
                                SELECT conversation_id, created_at,
                                       created_at - LAG(created_at) OVER (
                                           PARTITION BY conversation_id ORDER BY created_at) AS gap
                                FROM events
                            ) g
                            GROUP BY conversation_id
                        )
                        SELECT COUNT(*) AS sessions,
                               COALESCE(percentile_cont(0.5) WITHIN GROUP (
                                   ORDER BY COALESCE(m.message_count, 0)), 0) AS median_messages,
                               COALESCE(percentile_cont(0.9) WITHIN GROUP (
                                   ORDER BY COALESCE(m.message_count, 0)), 0) AS p90_messages,
                               COALESCE(SUM(COALESCE(r.tokens, 0)), 0) AS tokens,
                               COALESCE(SUM(COALESCE(r.units, 0)), 0) AS units,
                               COALESCE(SUM(COALESCE(r.cache_tokens, 0)), 0) AS cache_tokens,
                               COALESCE(SUM(COALESCE(a.active_seconds, 0)), 0)::bigint AS active_seconds
                        FROM scoped s
                        LEFT JOIN requests r ON r.conversation_id = s.id
                        LEFT JOIN messages m ON m.conversation_id = s.id
                        LEFT JOIN active a ON a.conversation_id = s.id
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("agent", agent)
                .bind("model", model)
                .map((row, metadata) -> mapSessionStatsTotals(row))
                .one();
    }

    public Flux<SessionStatsRow> sessionStatsSessions(
            Instant fromAt,
            Instant toAt,
            String agent,
            String model,
            boolean all,
            String userId) {
        return databaseClient.sql("""
                        WITH scoped AS (
                            SELECT c.id, c.title, c.agent_id, c.last_activity_at
                            FROM ai_conversation c
                            WHERE c.deleted_at IS NULL
                              AND c.last_activity_at >= :fromAt
                              AND c.last_activity_at < :toAt
                              AND (:all OR c.user_id = :userId)
                              AND (:agent = '' OR c.agent_id = :agent)
                              AND (:model = '' OR EXISTS (
                                  SELECT 1 FROM ai_chat_request rm
                                  WHERE rm.conversation_id = c.id
                                    AND rm.created_at >= :fromAt
                                    AND rm.created_at < :toAt
                                    AND rm.selected_model = :model
                              ))
                        ),
                        requests AS (
                            SELECT conversation_id,
                                   COUNT(*) AS request_count,
                                   COALESCE(SUM(total_tokens), 0) AS tokens,
                                   COALESCE(SUM(COALESCE(usage_units, points, 0)), 0) AS units,
                                   COALESCE(SUM(cache_tokens), 0) AS cache_tokens,
                                   (ARRAY_AGG(selected_model ORDER BY created_at DESC)
                                       FILTER (WHERE selected_model IS NOT NULL))[1] AS model
                            FROM ai_chat_request r
                            WHERE r.created_at >= :fromAt
                              AND r.created_at < :toAt
                              AND (:model = '' OR r.selected_model = :model)
                            GROUP BY conversation_id
                        ),
                        messages AS (
                            SELECT conversation_id, COUNT(*) AS message_count
                            FROM ai_message
                            WHERE deleted_at IS NULL
                              AND created_at >= :fromAt
                              AND created_at < :toAt
                            GROUP BY conversation_id
                        )
                        SELECT s.id AS conversation_id,
                               s.title,
                               s.agent_id,
                               r.model,
                               COALESCE(m.message_count, 0) AS message_count,
                               COALESCE(r.request_count, 0) AS request_count,
                               COALESCE(r.tokens, 0) AS tokens,
                               COALESCE(r.units, 0) AS units,
                               COALESCE(r.cache_tokens, 0) AS cache_tokens,
                               s.last_activity_at
                        FROM scoped s
                        LEFT JOIN requests r ON r.conversation_id = s.id
                        LEFT JOIN messages m ON m.conversation_id = s.id
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("agent", agent)
                .bind("model", model)
                .map((row, metadata) -> mapSessionStats(row))
                .all();
    }

    public Flux<SessionStatsDailyRow> sessionStatsDaily(
            Instant fromAt,
            Instant toAt,
            String agent,
            String model,
            boolean all,
            String userId) {
        return databaseClient.sql("""
                        WITH scoped AS (
                            SELECT c.id
                            FROM ai_conversation c
                            WHERE c.deleted_at IS NULL
                              AND c.last_activity_at >= :fromAt
                              AND c.last_activity_at < :toAt
                              AND (:all OR c.user_id = :userId)
                              AND (:agent = '' OR c.agent_id = :agent)
                              AND (:model = '' OR EXISTS (
                                  SELECT 1 FROM ai_chat_request rm
                                  WHERE rm.conversation_id = c.id
                                    AND rm.created_at >= :fromAt
                                    AND rm.created_at < :toAt
                                    AND rm.selected_model = :model
                              ))
                        ),
                        request_events AS (
                            SELECT r.conversation_id, r.created_at
                            FROM ai_chat_request r
                            JOIN scoped s ON s.id = r.conversation_id
                            WHERE r.created_at >= :fromAt
                              AND r.created_at < :toAt
                              AND (:model = '' OR r.selected_model = :model)
                        ),
                        message_events AS (
                            SELECT m.conversation_id, m.created_at
                            FROM ai_message m
                            JOIN scoped s ON s.id = m.conversation_id
                            WHERE m.deleted_at IS NULL
                              AND m.created_at >= :fromAt
                              AND m.created_at < :toAt
                        ),
                        event_days AS (
                            SELECT conversation_id,
                                   date_trunc('day', created_at AT TIME ZONE 'Asia/Shanghai') AS day,
                                   created_at
                            FROM (
                                SELECT conversation_id, created_at FROM request_events
                                UNION ALL
                                SELECT conversation_id, created_at FROM message_events
                            ) e
                        ),
                        active AS (
                            SELECT conversation_id, day,
                                   SUM(EXTRACT(EPOCH FROM gap)) FILTER (
                                       WHERE gap IS NOT NULL AND gap <= INTERVAL '5 minutes'
                                   ) AS seconds
                            FROM (
                                SELECT conversation_id, day, created_at,
                                       created_at - LAG(created_at) OVER (
                                           PARTITION BY conversation_id, day ORDER BY created_at) AS gap
                                FROM event_days
                            ) g
                            GROUP BY conversation_id, day
                        ),
                        sessions AS (
                            SELECT day, COUNT(*) AS sessions
                            FROM (SELECT DISTINCT conversation_id, day FROM event_days) d
                            GROUP BY day
                        ),
                        messages AS (
                            SELECT date_trunc('day', m.created_at AT TIME ZONE 'Asia/Shanghai') AS day,
                                   COUNT(*) AS messages
                            FROM ai_message m
                            JOIN scoped s ON s.id = m.conversation_id
                            WHERE m.deleted_at IS NULL
                              AND m.created_at >= :fromAt
                              AND m.created_at < :toAt
                            GROUP BY day
                        ),
                        usage_units AS (
                            SELECT date_trunc('day', r.created_at AT TIME ZONE 'Asia/Shanghai') AS day,
                                   COALESCE(SUM(r.total_tokens), 0) AS tokens,
                                   COALESCE(SUM(COALESCE(r.usage_units, r.points, 0)), 0) AS units
                            FROM ai_chat_request r
                            JOIN scoped s ON s.id = r.conversation_id
                            WHERE r.created_at >= :fromAt
                              AND r.created_at < :toAt
                              AND (:model = '' OR r.selected_model = :model)
                            GROUP BY day
                        ),
                        active_by_day AS (
                            SELECT day, SUM(seconds) AS seconds FROM active GROUP BY day
                        ),
                        days AS (
                            SELECT generate_series(
                                       date_trunc('day', :fromAt AT TIME ZONE 'Asia/Shanghai'),
                                       date_trunc('day', :toAt AT TIME ZONE 'Asia/Shanghai')
                                           - INTERVAL '1 microsecond',
                                       INTERVAL '1 day') AS day
                        )
                        SELECT to_char(d.day, 'YYYY-MM-DD') AS day,
                               COALESCE(s.sessions, 0) AS sessions,
                               COALESCE(m.messages, 0) AS messages,
                               COALESCE(p.tokens, 0) AS tokens,
                               COALESCE(p.units, 0) AS units,
                               COALESCE(a.seconds, 0)::bigint AS active_seconds
                        FROM days d
                        LEFT JOIN sessions s ON s.day = d.day
                        LEFT JOIN messages m ON m.day = d.day
                        LEFT JOIN usage_units p ON p.day = d.day
                        LEFT JOIN active_by_day a ON a.day = d.day
                        ORDER BY d.day
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("agent", agent)
                .bind("model", model)
                .map((row, metadata) -> mapSessionStatsDaily(row))
                .all();
    }

    public Flux<SessionStatsHourRow> sessionStatsHours(
            Instant fromAt,
            Instant toAt,
            String agent,
            String model,
            boolean all,
            String userId) {
        return databaseClient.sql("""
                        WITH scoped AS (
                            SELECT c.id
                            FROM ai_conversation c
                            WHERE c.deleted_at IS NULL
                              AND c.last_activity_at >= :fromAt
                              AND c.last_activity_at < :toAt
                              AND (:all OR c.user_id = :userId)
                              AND (:agent = '' OR c.agent_id = :agent)
                              AND (:model = '' OR EXISTS (
                                  SELECT 1 FROM ai_chat_request rm
                                  WHERE rm.conversation_id = c.id
                                    AND rm.created_at >= :fromAt
                                    AND rm.created_at < :toAt
                                    AND rm.selected_model = :model
                              ))
                        ),
                        events AS (
                            SELECT r.conversation_id, r.created_at
                            FROM ai_chat_request r
                            JOIN scoped s ON s.id = r.conversation_id
                            WHERE r.created_at >= :fromAt
                              AND r.created_at < :toAt
                              AND (:model = '' OR r.selected_model = :model)
                            UNION ALL
                            SELECT m.conversation_id, m.created_at
                            FROM ai_message m
                            JOIN scoped s ON s.id = m.conversation_id
                            WHERE m.deleted_at IS NULL
                              AND m.created_at >= :fromAt
                              AND m.created_at < :toAt
                        )
                        SELECT EXTRACT(HOUR FROM created_at AT TIME ZONE 'Asia/Shanghai')::int AS hour,
                               COUNT(DISTINCT conversation_id) AS sessions
                        FROM events
                        GROUP BY 1
                        ORDER BY 1
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("agent", agent)
                .bind("model", model)
                .map((row, metadata) -> mapSessionStatsHour(row))
                .all();
    }

    public Flux<ContextGridRow> contextGrid(
            Instant fromAt,
            Instant toAt,
            String agent,
            String model,
            boolean all,
            String userId,
            int limit) {
        return databaseClient.sql("""
                        SELECT c.request_id,
                               c.agent_id,
                               COALESCE(c.selected_model, 'unknown') AS model,
                               c.created_at,
                               COALESCE(c.input_tokens, 0) AS input_tokens,
                               COALESCE(c.output_tokens, 0) AS output_tokens,
                               COALESCE(CASE WHEN c.route_trace ->> 'memoryTokens' ~ '^[0-9]+$'
                                             THEN (c.route_trace ->> 'memoryTokens')::bigint
                                             ELSE 0 END, 0) AS memory_tokens,
                               COALESCE(CASE WHEN c.retrieval_trace ->> 'kbTokens' ~ '^[0-9]+$'
                                             THEN (c.retrieval_trace ->> 'kbTokens')::bigint
                                             ELSE 0 END, 0) AS kb_tokens,
                               COALESCE(msgs.user_chars, 0) AS user_chars,
                               COALESCE(msgs.user_cjk_chars, 0) AS user_cjk_chars
                        FROM ai_chat_request c
                        LEFT JOIN (
                            SELECT m.request_id,
                                   SUM(char_length(COALESCE(m.content, ''))) AS user_chars,
                                   SUM(char_length(regexp_replace(
                                       COALESCE(m.content, ''), '[^一-鿿㐀-䶿぀-ヿ가-힯]', '', 'g')))
                                       AS user_cjk_chars
                            FROM ai_message m
                            WHERE m.deleted_at IS NULL
                              AND m.role = 'user'
                              AND m.request_id IS NOT NULL
                              AND m.created_at >= :fromAt
                              AND m.created_at < :toAt
                            GROUP BY m.request_id
                        ) msgs ON msgs.request_id = c.request_id
                        WHERE c.created_at >= :fromAt
                          AND c.created_at < :toAt
                          AND (:all OR c.user_id = :userId)
                          AND (:agent = '' OR c.agent_id = :agent)
                          AND (:model = '' OR c.selected_model = :model)
                        ORDER BY c.created_at DESC, c.id DESC
                        LIMIT :limit
                        """)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("agent", agent)
                .bind("model", model)
                .bind("limit", limit)
                .map((row, metadata) -> mapContextGrid(row))
                .all();
    }

    public Mono<Long> deleteChatRequests(Instant beforeAt, String provider, String model) {
        return databaseClient.sql("""
                        DELETE FROM ai_chat_request
                        WHERE created_at < :beforeAt
                          AND (:provider = '' OR selected_provider = :provider)
                          AND (:model = '' OR selected_model = :model)
                        """)
                .bind("beforeAt", time(beforeAt))
                .bind("provider", provider)
                .bind("model", model)
                .fetch()
                .rowsUpdated();
    }

    public Mono<Long> deleteModelRequests(Instant beforeAt, String provider, String model) {
        return databaseClient.sql("""
                        DELETE FROM ai_model_request
                        WHERE created_at < :beforeAt
                          AND (:provider = '' OR selected_provider = :provider)
                          AND (:model = '' OR selected_model = :model)
                        """)
                .bind("beforeAt", time(beforeAt))
                .bind("provider", provider)
                .bind("model", model)
                .fetch()
                .rowsUpdated();
    }

    private DatabaseClient.GenericExecuteSpec requestQuery(
            String sql,
            Instant fromAt,
            Instant toAt,
            String status,
            String provider,
            String model,
            String account,
            String agent,
            String agentSource,
            String mode,
            String routeGroup,
            String q,
            String purpose,
            String via,
            String callerKey,
            boolean retried,
            boolean all,
            String userId) {
        return databaseClient.sql(sql)
                .bind("fromAt", time(fromAt))
                .bind("toAt", time(toAt))
                .bind("all", all)
                .bind("userId", userId)
                .bind("status", status)
                .bind("provider", provider)
                .bind("model", model)
                .bind("account", account)
                .bind("agent", agent)
                .bind("agentSource", agentSource)
                .bind("mode", mode)
                .bind("routeGroup", routeGroup)
                .bind("q", q)
                .bind("qPattern", pattern(q))
                .bind("purpose", purpose)
                .bind("via", via)
                .bind("callerKey", callerKey)
                .bind("retried", retried);
    }

    static OverviewDailyRow mapDaily(Row row) {
        return new OverviewDailyRow(
                row.get("day", String.class),
                count(row, "requests"),
                count(row, "succeeded"),
                count(row, "failed"),
                count(row, "input_tokens"),
                count(row, "cache_tokens"),
                count(row, "output_tokens"),
                count(row, "total_tokens"),
                decimal(row, "units"));
    }

    static OverviewRankingRow mapRanking(Row row) {
        return new OverviewRankingRow(
                row.get("agent", String.class),
                row.get("model", String.class),
                row.get("provider", String.class),
                row.get("account", String.class),
                count(row, "requests"),
                count(row, "succeeded"),
                count(row, "failed"),
                count(row, "canceled_requests"),
                count(row, "unpriced_requests"),
                count(row, "retried_requests"),
                count(row, "reasoning_tokens"),
                count(row, "input_tokens"),
                count(row, "cache_tokens"),
                count(row, "output_tokens"),
                count(row, "total_tokens"),
                decimal(row, "units"),
                count(row, "latency_sum"),
                count(row, "latency_count"),
                count(row, "ttft_sum"),
                count(row, "ttft_count"),
                decimal(row, "tps_sum"),
                count(row, "tps_count"));
    }

    static RequestTotalsRow mapRequestTotals(Row row) {
        return new RequestTotalsRow(
                count(row, "requests"),
                count(row, "succeeded"),
                count(row, "failed"),
                count(row, "canceled_requests"),
                count(row, "unpriced_requests"),
                count(row, "retried_requests"),
                count(row, "reasoning_tokens"),
                count(row, "input_tokens"),
                count(row, "cache_tokens"),
                count(row, "output_tokens"),
                count(row, "total_tokens"),
                decimal(row, "units"));
    }

    static RequestRow mapRequest(Row row) {
        return new RequestRow(
                row.get("id", Long.class),
                row.get("request_id", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("agent_source", String.class),
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
                row.get("latency_ms", Integer.class),
                row.get("ttft_ms", Integer.class),
                row.get("tps", BigDecimal.class),
                row.get("session_key", String.class),
                row.get("conversation_id", Long.class),
                Boolean.TRUE.equals(row.get("retried", Boolean.class)),
                row.get("served_effort", String.class),
                row.get("created_at", Instant.class),
                row.get("completed_at", Instant.class));
    }

    static RequestDetailRow mapRequestDetail(Row row) {
        return new RequestDetailRow(
                row.get("request_id", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("agent_source", String.class),
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
                row.get("billing_source", String.class),
                row.get("billing_status", String.class),
                row.get("usage_source", String.class),
                row.get("upstream_request_id", String.class),
                row.get("latency_ms", Integer.class),
                row.get("ttft_ms", Integer.class),
                row.get("tps", BigDecimal.class),
                row.get("session_key", String.class),
                row.get("conversation_id", Long.class),
                json(row.get("route_trace", String.class)),
                json(row.get("retrieval_trace", String.class)),
                row.get("created_at", Instant.class),
                row.get("completed_at", Instant.class));
    }

    static SessionRow mapSession(Row row) {
        return new SessionRow(
                row.get("conversation_id", Long.class),
                row.get("title", String.class),
                row.get("user_id", String.class),
                row.get("team_id", String.class),
                row.get("agent_id", String.class),
                row.get("agent_source", String.class),
                row.get("status", String.class),
                row.get("model", String.class),
                count(row, "message_count"),
                count(row, "request_count"),
                count(row, "input_tokens"),
                count(row, "cache_tokens"),
                count(row, "output_tokens"),
                count(row, "total_tokens"),
                decimal(row, "units"),
                row.get("created_at", Instant.class),
                row.get("last_activity_at", Instant.class));
    }

    static SessionUsageRow mapSessionUsage(Row row) {
        return new SessionUsageRow(
                count(row, "request_count"),
                count(row, "input_tokens"),
                count(row, "cache_tokens"),
                count(row, "output_tokens"),
                count(row, "total_tokens"),
                decimal(row, "units"),
                row.get("model", String.class),
                count(row, "message_count"),
                count(row, "user_message_count"),
                count(row, "assistant_message_count"));
    }

    static MessageRow mapMessage(Row row) {
        return new MessageRow(
                row.get("message_id", Long.class),
                row.get("role", String.class),
                row.get("content_preview", String.class),
                row.get("token_count", Integer.class),
                row.get("model_code", String.class),
                row.get("created_at", Instant.class));
    }

    static ContextRow mapContext(Row row) {
        return new ContextRow(
                row.get("agent_id", String.class),
                row.get("model", String.class),
                count(row, "requests"),
                count(row, "input_tokens"),
                count(row, "output_tokens"),
                count(row, "user_chars"),
                count(row, "user_cjk_chars"),
                count(row, "memory_tokens"),
                count(row, "kb_tokens"));
    }

    static SpeedRow mapSpeed(Row row) {
        return new SpeedRow(
                row.get("name", String.class),
                count(row, "requests"),
                count(row, "failures"),
                row.get("avg_ttft_ms", Double.class),
                row.get("p50_ttft_ms", Double.class),
                row.get("avg_tps", Double.class),
                row.get("p50_tps", Double.class));
    }

    static SessionStatsTotalsRow mapSessionStatsTotals(Row row) {
        return new SessionStatsTotalsRow(
                count(row, "sessions"),
                row.get("median_messages", Double.class),
                row.get("p90_messages", Double.class),
                count(row, "tokens"),
                decimal(row, "units"),
                count(row, "cache_tokens"),
                count(row, "active_seconds"));
    }

    static SessionStatsRow mapSessionStats(Row row) {
        return new SessionStatsRow(
                row.get("conversation_id", Long.class),
                row.get("title", String.class),
                row.get("agent_id", String.class),
                row.get("model", String.class),
                count(row, "message_count"),
                count(row, "request_count"),
                count(row, "tokens"),
                decimal(row, "units"),
                count(row, "cache_tokens"),
                row.get("last_activity_at", Instant.class));
    }

    static SessionStatsDailyRow mapSessionStatsDaily(Row row) {
        return new SessionStatsDailyRow(
                row.get("day", String.class),
                count(row, "sessions"),
                count(row, "messages"),
                count(row, "tokens"),
                decimal(row, "units"),
                count(row, "active_seconds"));
    }

    static SessionStatsHourRow mapSessionStatsHour(Row row) {
        var hour = row.get("hour", Integer.class);
        return new SessionStatsHourRow(hour == null ? 0 : hour, count(row, "sessions"));
    }

    static ContextGridRow mapContextGrid(Row row) {
        return new ContextGridRow(
                row.get("request_id", String.class),
                row.get("agent_id", String.class),
                row.get("model", String.class),
                row.get("created_at", Instant.class),
                count(row, "input_tokens"),
                count(row, "output_tokens"),
                count(row, "memory_tokens"),
                count(row, "kb_tokens"),
                count(row, "user_chars"),
                count(row, "user_cjk_chars"));
    }

    private static @Nullable JsonNode json(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return JsonUtils.jsonToObject(value, JsonNode.class);
    }

    private static String pattern(String value) {
        return "%" + value + "%";
    }

    private static OffsetDateTime time(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public record OverviewDailyRow(
            String day,
            long requests,
            long succeeded,
            long failed,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            long totalTokens,
            BigDecimal units) {}

    public record OverviewRankingRow(
            String agent,
            String model,
            String provider,
            String account,
            long requests,
            long succeeded,
            long failed,
            long canceledRequests,
            long unpricedRequests,
            long retriedRequests,
            long reasoningTokens,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            long totalTokens,
            BigDecimal units,
            long latencySum,
            long latencyCount,
            long ttftSum,
            long ttftCount,
            BigDecimal tpsSum,
            long tpsCount) {}

    public record RequestTotalsRow(
            long requests,
            long succeeded,
            long failed,
            long canceledRequests,
            long unpricedRequests,
            long retriedRequests,
            long reasoningTokens,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            long totalTokens,
            BigDecimal units) {}

    public record RequestRow(
            long id,
            String requestId,
            String userId,
            String teamId,
            String agent,
            String agentSource,
            @Nullable String mode,
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
            @Nullable Long units,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            @Nullable String sessionKey,
            @Nullable Long conversationId,
            boolean retried,
            @Nullable String servedEffort,
            Instant createdAt,
            @Nullable Instant completedAt) {}

    public record RequestDetailRow(
            String requestId,
            String userId,
            String teamId,
            String agent,
            String agentSource,
            @Nullable String mode,
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
            @Nullable Long units,
            @Nullable String billingSource,
            String billingStatus,
            @Nullable String usageSource,
            @Nullable String upstreamRequestId,
            @Nullable Integer latencyMs,
            @Nullable Integer ttftMs,
            @Nullable BigDecimal tps,
            @Nullable String sessionKey,
            @Nullable Long conversationId,
            @Nullable JsonNode routeTrace,
            @Nullable JsonNode retrievalTrace,
            Instant createdAt,
            @Nullable Instant completedAt) {}

    public record SessionRow(
            long conversationId,
            @Nullable String title,
            String userId,
            String teamId,
            String agent,
            String agentSource,
            String status,
            @Nullable String model,
            long messageCount,
            long requestCount,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            long totalTokens,
            BigDecimal units,
            Instant createdAt,
            Instant lastActivityAt) {}

    public record SessionUsageRow(
            long requestCount,
            long inputTokens,
            long cacheTokens,
            long outputTokens,
            long totalTokens,
            BigDecimal units,
            @Nullable String model,
            long messageCount,
            long userMessageCount,
            long assistantMessageCount) {}

    public record MessageRow(
            long messageId,
            String role,
            String content,
            @Nullable Integer tokenCount,
            @Nullable String model,
            Instant createdAt) {}

    public record ContextRow(
            String agent,
            String model,
            long requests,
            long inputTokens,
            long outputTokens,
            long userChars,
            long userCjkChars,
            long memoryTokens,
            long kbTokens) {}

    public record SpeedRow(
            String name,
            long requests,
            long failures,
            @Nullable Double avgTtftMs,
            @Nullable Double p50TtftMs,
            @Nullable Double avgTps,
            @Nullable Double p50Tps) {}

    public record SessionStatsTotalsRow(
            long sessions,
            @Nullable Double medianMessages,
            @Nullable Double p90Messages,
            long tokens,
            BigDecimal units,
            long cacheTokens,
            long activeSeconds) {}

    public record SessionStatsRow(
            long conversationId,
            @Nullable String title,
            String agent,
            @Nullable String model,
            long messageCount,
            long requestCount,
            long tokens,
            BigDecimal units,
            long cacheTokens,
            Instant lastActivityAt) {}

    public record SessionStatsDailyRow(
            String day,
            long sessions,
            long messages,
            long tokens,
            BigDecimal units,
            long activeSeconds) {}

    public record SessionStatsHourRow(int hour, long sessions) {}

    public record ContextGridRow(
            String requestId,
            String agent,
            String model,
            Instant createdAt,
            long inputTokens,
            long outputTokens,
            long memoryTokens,
            long kbTokens,
            long userChars,
            long userCjkChars) {}
}