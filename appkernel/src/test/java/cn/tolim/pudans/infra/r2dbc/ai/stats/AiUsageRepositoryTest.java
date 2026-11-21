package cn.tolim.pudans.infra.r2dbc.ai.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.ContextGridRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.OverviewRankingRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestDetailRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsDailyRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionUsageRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SpeedRow;
import io.r2dbc.spi.Row;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.function.BiFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.RowsFetchSpec;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiUsageRepositoryTest {

    private static final Instant FROM = Instant.parse("2026-11-12T16:00:00Z");

    private static final Instant TO = Instant.parse("2026-11-19T16:00:00Z");

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");

    @Mock
    DatabaseClient databaseClient;

    @Mock
    DatabaseClient.GenericExecuteSpec spec;

    @Mock
    Row row;

    private AiUsageRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AiUsageRepository(databaseClient);
    }

    @Test
    void overviewRankingsBindsTheRangeAndFilters() {
        var fetchSpec = fetchSpec();
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.just(rankingRow()));

        StepVerifier.create(repository.overviewRankings(
                        FROM, TO, "team-a", "agent-a", "deepseek", "deepseek-chat", false, "user-1"))
                .expectNext(rankingRow())
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("FROM ai_chat_request")
                .contains("UNION ALL")
                .contains("FROM ai_model_request")
                .contains("COALESCE(usage_units, points) AS units")
                .contains("COUNT(*) FILTER (WHERE units IS NULL) AS unpriced_requests")
                .contains("GROUP BY agent_id, selected_model, selected_provider, selected_account")
                .contains("(:all OR user_id = :userId)");
        verify(spec).bind(eq("fromAt"), any(OffsetDateTime.class));
        verify(spec).bind(eq("toAt"), any(OffsetDateTime.class));
        verify(spec).bind("team", "team-a");
        verify(spec).bind("agent", "agent-a");
        verify(spec).bind("provider", "deepseek");
        verify(spec).bind("model", "deepseek-chat");
        verify(spec).bind("all", false);
        verify(spec).bind("userId", "user-1");
    }

    @Test
    void overviewSeriesGroupsByTheShanghaiDay() {
        var fetchSpec = fetchSpec();
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.overviewSeries(FROM, TO, "", "", "", "", "day", false, "user-1"))
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("date_trunc('day', created_at AT TIME ZONE 'Asia/Shanghai')")
                .contains("GROUP BY day")
                .contains("ORDER BY day")
                .contains("date_trunc('week'");
        verify(spec).bind(eq("fromAt"), any(OffsetDateTime.class));
        verify(spec).bind(eq("toAt"), any(OffsetDateTime.class));
        verify(spec).bind("bucket", "day");
        verify(spec).bind("all", false);
        verify(spec).bind("userId", "user-1");
    }

    @Test
    void requestsBindsTheCursorKeywordAndLimit() {
        var fetchSpec = fetchSpec();
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.just(requestRow()));

        StepVerifier.create(repository.requests(
                        FROM,
                        TO,
                        "SUCCEEDED",
                        "deepseek",
                        "deepseek-chat",
                        "main",
                        "agent-a",
                        "gateway-agent",
                        "chat",
                        "default",
                        "req",
                        "agent-run",
                        "gateway",
                        "key-1",
                        true,
                        false,
                        "user-1",
                        42L,
                        51))
                .expectNext(requestRow())
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("AND id < :beforeId")
                .contains("ORDER BY id DESC")
                .contains("LIMIT :limit")
                .contains("request_id ILIKE :qPattern")
                .contains("COALESCE(usage_units, points) AS units")
                .contains("route_trace ->> 'purpose' = :purpose")
                .contains("route_trace ->> 'callerKey' = :callerKey")
                .contains("agent_source = :agentSource")
                .contains("route_trace ->> 'tries'");
        verify(spec).bind("status", "SUCCEEDED");
        verify(spec).bind("agentSource", "gateway-agent");
        verify(spec).bind("q", "req");
        verify(spec).bind("qPattern", "%req%");
        verify(spec).bind("purpose", "agent-run");
        verify(spec).bind("via", "gateway");
        verify(spec).bind("callerKey", "key-1");
        verify(spec).bind("retried", true);
        verify(spec).bind("all", false);
        verify(spec).bind("userId", "user-1");
        verify(spec).bind("beforeId", 42L);
        verify(spec).bind("limit", 51);
    }

    @Test
    void sessionsBindTheAgentSearchAndCursor() {
        var fetchSpec = fetchSpec();
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.just(sessionRow()));

        StepVerifier.create(repository.sessions(FROM, TO, "agent-a", "复盘", "", "", "", "", false, "user-1", 9L, 20))
                .expectNext(sessionRow())
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("FROM ai_conversation")
                .contains("last_activity_at >= :fromAt")
                .contains("c.id < :beforeId")
                .contains("c.title ILIKE :qPattern");
        verify(spec).bind("agent", "agent-a");
        verify(spec).bind("all", false);
        verify(spec).bind("userId", "user-1");
        verify(spec).bind("qPattern", "%复盘%");
        verify(spec).bind("beforeId", 9L);
        verify(spec).bind("limit", 20);
    }

    @Test
    void mapsTheRequestRow() {
        when(row.get("id", Long.class)).thenReturn(5L);
        when(row.get("request_id", String.class)).thenReturn("req-5");
        when(row.get("user_id", String.class)).thenReturn("user-1");
        when(row.get("team_id", String.class)).thenReturn("team-a");
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("agent_source", String.class)).thenReturn("claude-code");
        when(row.get("mode_code", String.class)).thenReturn("chat");
        when(row.get("route_group", String.class)).thenReturn("default");
        when(row.get("selected_provider", String.class)).thenReturn("deepseek");
        when(row.get("selected_account", String.class)).thenReturn("main");
        when(row.get("selected_model", String.class)).thenReturn("deepseek-chat");
        when(row.get("tier", String.class)).thenReturn("HIGH");
        when(row.get("status", String.class)).thenReturn("SUCCEEDED");
        when(row.get("input_tokens", Integer.class)).thenReturn(900);
        when(row.get("cache_tokens", Integer.class)).thenReturn(700);
        when(row.get("output_tokens", Integer.class)).thenReturn(120);
        when(row.get("total_tokens", Integer.class)).thenReturn(1020);
        when(row.get("usage_units", Long.class)).thenReturn(3L);
        when(row.get("latency_ms", Integer.class)).thenReturn(3200);
        when(row.get("ttft_ms", Integer.class)).thenReturn(500);
        when(row.get("tps", BigDecimal.class)).thenReturn(new BigDecimal("37.50"));
        when(row.get("session_key", String.class)).thenReturn("session-1");
        when(row.get("conversation_id", Long.class)).thenReturn(9L);
        when(row.get("retried", Boolean.class)).thenReturn(true);
        when(row.get("served_effort", String.class)).thenReturn("high");
        when(row.get("created_at", Instant.class)).thenReturn(NOW);
        when(row.get("completed_at", Instant.class)).thenReturn(NOW);
        when(row.get("error_code", String.class)).thenReturn(null);

        var mapped = AiUsageRepository.mapRequest(row);

        assertThat(mapped.id()).isEqualTo(5L);
        assertThat(mapped.requestId()).isEqualTo("req-5");
        assertThat(mapped.agent()).isEqualTo("agent-a");
        assertThat(mapped.agentSource()).isEqualTo("claude-code");
        assertThat(mapped.inputTokens()).isEqualTo(900);
        assertThat(mapped.units()).isEqualTo(3L);
        assertThat(mapped.ttftMs()).isEqualTo(500);
        assertThat(mapped.tps()).isEqualByComparingTo("37.50");
        assertThat(mapped.retried()).isTrue();
        assertThat(mapped.servedEffort()).isEqualTo("high");
        assertThat(mapped.createdAt()).isEqualTo(NOW);
    }

    @Test
    void mapsTheRequestDetailTraces() {
        when(row.get("request_id", String.class)).thenReturn("req-5");
        when(row.get("user_id", String.class)).thenReturn("user-1");
        when(row.get("team_id", String.class)).thenReturn("team-a");
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("agent_source", String.class)).thenReturn("claude-code");
        when(row.get("mode_code", String.class)).thenReturn("chat");
        when(row.get("route_group", String.class)).thenReturn("default");
        when(row.get("selected_provider", String.class)).thenReturn("deepseek");
        when(row.get("selected_account", String.class)).thenReturn("main");
        when(row.get("selected_model", String.class)).thenReturn("deepseek-chat");
        when(row.get("tier", String.class)).thenReturn("HIGH");
        when(row.get("status", String.class)).thenReturn("SUCCEEDED");
        when(row.get("error_code", String.class)).thenReturn(null);
        when(row.get("input_tokens", Integer.class)).thenReturn(900);
        when(row.get("cache_tokens", Integer.class)).thenReturn(700);
        when(row.get("output_tokens", Integer.class)).thenReturn(120);
        when(row.get("total_tokens", Integer.class)).thenReturn(1020);
        when(row.get("usage_units", Long.class)).thenReturn(3L);
        when(row.get("billing_source", String.class)).thenReturn("PROVIDER");
        when(row.get("billing_status", String.class)).thenReturn("SETTLED");
        when(row.get("usage_source", String.class)).thenReturn("PROVIDER");
        when(row.get("upstream_request_id", String.class)).thenReturn("upstream-1");
        when(row.get("latency_ms", Integer.class)).thenReturn(3200);
        when(row.get("ttft_ms", Integer.class)).thenReturn(500);
        when(row.get("tps", BigDecimal.class)).thenReturn(new BigDecimal("37.50"));
        when(row.get("session_key", String.class)).thenReturn("session-1");
        when(row.get("conversation_id", Long.class)).thenReturn(9L);
        when(row.get("route_trace", String.class)).thenReturn("{\"attempts\":2}");
        when(row.get("retrieval_trace", String.class)).thenReturn("{\"kbTokens\":120}");
        when(row.get("created_at", Instant.class)).thenReturn(NOW);
        when(row.get("completed_at", Instant.class)).thenReturn(NOW);

        var mapped = AiUsageRepository.mapRequestDetail(row);

        assertThat(mapped.requestId()).isEqualTo("req-5");
        assertThat(mapped.units()).isEqualTo(3L);
        assertThat(mapped.billingSource()).isEqualTo("PROVIDER");
        assertThat(mapped.billingStatus()).isEqualTo("SETTLED");
        assertThat(mapped.upstreamRequestId()).isEqualTo("upstream-1");
        assertThat(mapped.routeTrace().get("attempts").asInt()).isEqualTo(2);
        assertThat(mapped.retrievalTrace().get("kbTokens").asLong()).isEqualTo(120L);
    }

    @Test
    void mapsTheSessionUsageCounts() {
        when(row.get("request_count", Long.class)).thenReturn(4L);
        when(row.get("input_tokens", Long.class)).thenReturn(1200L);
        when(row.get("cache_tokens", Long.class)).thenReturn(300L);
        when(row.get("output_tokens", Long.class)).thenReturn(800L);
        when(row.get("total_tokens", Long.class)).thenReturn(2300L);
        when(row.get("units", BigDecimal.class)).thenReturn(new BigDecimal("0.42"));
        when(row.get("model", String.class)).thenReturn("deepseek-chat");
        when(row.get("message_count", Long.class)).thenReturn(8L);
        when(row.get("user_message_count", Long.class)).thenReturn(4L);
        when(row.get("assistant_message_count", Long.class)).thenReturn(4L);

        var mapped = AiUsageRepository.mapSessionUsage(row);

        assertThat(mapped.requestCount()).isEqualTo(4L);
        assertThat(mapped.totalTokens()).isEqualTo(2300L);
        assertThat(mapped.model()).isEqualTo("deepseek-chat");
        assertThat(mapped.messageCount()).isEqualTo(8L);
        assertThat(mapped.userMessageCount()).isEqualTo(4L);
        assertThat(mapped.assistantMessageCount()).isEqualTo(4L);
    }

    @Test
    void mapsTheContextCompositionColumns() {
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("model", String.class)).thenReturn("deepseek-chat");
        when(row.get("requests", Long.class)).thenReturn(3L);
        when(row.get("input_tokens", Long.class)).thenReturn(1500L);
        when(row.get("output_tokens", Long.class)).thenReturn(400L);
        when(row.get("user_chars", Long.class)).thenReturn(200L);
        when(row.get("user_cjk_chars", Long.class)).thenReturn(40L);
        when(row.get("memory_tokens", Long.class)).thenReturn(15L);
        when(row.get("kb_tokens", Long.class)).thenReturn(25L);

        var mapped = AiUsageRepository.mapContext(row);

        assertThat(mapped.agent()).isEqualTo("agent-a");
        assertThat(mapped.requests()).isEqualTo(3L);
        assertThat(mapped.userChars()).isEqualTo(200L);
        assertThat(mapped.userCjkChars()).isEqualTo(40L);
        assertThat(mapped.memoryTokens()).isEqualTo(15L);
        assertThat(mapped.kbTokens()).isEqualTo(25L);
    }

    @Test
    void speedGroupsByTheRequestedDimensionAndUsesPercentiles() {
        var fetchSpec = fetchSpec();
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.just(
                new SpeedRow("deepseek-chat", 10, 1, 210.4, 198.6, 32.456, 30.1)));

        StepVerifier.create(repository.speed(FROM, TO, "provider", true, "user-1"))
                .expectNextCount(1)
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("CASE WHEN :groupBy = 'provider'")
                .contains("percentile_cont(0.5) WITHIN GROUP (ORDER BY ttft_ms)")
                .contains("percentile_cont(0.5) WITHIN GROUP (ORDER BY tps)")
                .contains("(:all OR user_id = :userId)");
        verify(spec).bind("groupBy", "provider");
        verify(spec).bind("all", true);
        verify(spec).bind("userId", "user-1");
    }

    @Test
    void sessionStatsDailySplitsActivityAtFiveMinuteGaps() {
        var fetchSpec = fetchSpec();
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.just(new SessionStatsDailyRow(
                "2026-11-19", 2, 125, 2000, new BigDecimal("0.50"), 180)));

        StepVerifier.create(repository.sessionStatsDaily(
                        FROM, TO, "agent-a", "deepseek-chat", false, "user-1"))
                .expectNextCount(1)
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("generate_series")
                .contains("INTERVAL '5 minutes'")
                .contains("date_trunc('day',")
                .contains("AT TIME ZONE 'Asia/Shanghai'");
        verify(spec).bind("agent", "agent-a");
        verify(spec).bind("model", "deepseek-chat");
        verify(spec).bind("all", false);
        verify(spec).bind("userId", "user-1");
    }

    @Test
    void contextGridScopesTheRowsAndBindsTheLimit() {
        var fetchSpec = fetchSpec();
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.just(new ContextGridRow(
                "req-1", "agent-a", "deepseek-chat", NOW, 1000, 200, 20, 30, 40, 0)));

        StepVerifier.create(repository.contextGrid(
                        FROM, TO, "agent-a", "deepseek-chat", false, "user-1", 400))
                .expectNextCount(1)
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("ORDER BY c.created_at DESC")
                .contains("LIMIT :limit")
                .contains("(:all OR c.user_id = :userId)");
        verify(spec).bind("agent", "agent-a");
        verify(spec).bind("model", "deepseek-chat");
        verify(spec).bind("all", false);
        verify(spec).bind("userId", "user-1");
        verify(spec).bind("limit", 400);
    }

    private static <T> RowsFetchSpec<T> fetchSpec() {
        @SuppressWarnings("unchecked")
        var fetchSpec = (RowsFetchSpec<T>) org.mockito.Mockito.mock(RowsFetchSpec.class);
        return fetchSpec;
    }

    private static OverviewRankingRow rankingRow() {
        return new OverviewRankingRow(
                "agent-a",
                "deepseek-chat",
                "deepseek",
                "main",
                5,
                4,
                1,
                2,
                1,
                3,
                40,
                1200,
                300,
                800,
                2300,
                new BigDecimal("0.42"),
                6000,
                5,
                1500,
                5,
                new BigDecimal("40.00"),
                5);
    }

    private static RequestRow requestRow() {
        return new RequestRow(
                5L,
                "req-5",
                "user-1",
                "team-a",
                "agent-a",
                "claude-code",
                "chat",
                "default",
                "deepseek",
                "main",
                "deepseek-chat",
                "HIGH",
                "SUCCEEDED",
                null,
                900,
                700,
                120,
                1020,
                3L,
                3200,
                500,
                new BigDecimal("37.50"),
                "session-1",
                9L,
                true,
                "high",
                NOW,
                NOW);
    }

    private static SessionRow sessionRow() {
        return new SessionRow(
                9L,
                "季度复盘",
                "user-1",
                "team-a",
                "agent-a",
                "pudans-chat",
                "ACTIVE",
                "deepseek-chat",
                8,
                4,
                1200,
                300,
                800,
                2300,
                new BigDecimal("0.42"),
                NOW.minusSeconds(3600),
                NOW);
    }

}