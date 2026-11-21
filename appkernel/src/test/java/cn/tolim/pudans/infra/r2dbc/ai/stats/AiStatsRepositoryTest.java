package cn.tolim.pudans.infra.r2dbc.ai.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.AccountActivityRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.PointStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RoutingHistoryRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RoutingSummaryRow;
import io.r2dbc.spi.Row;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiStatsRepositoryTest {

    private static final Instant FROM = Instant.parse("2026-11-12T16:00:00Z");

    private static final Instant TO = Instant.parse("2026-11-19T16:00:00Z");

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");

    @Mock
    DatabaseClient databaseClient;

    @Mock
    DatabaseClient.GenericExecuteSpec spec;

    @Mock
    Row row;

    private AiStatsRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AiStatsRepository(databaseClient);
    }

    @Test
    void routingHistoryJoinsTheConversationAndBindsTheFilters() {
        @SuppressWarnings("unchecked")
        var fetchSpec = (RowsFetchSpec<RoutingHistoryRow>) org.mockito.Mockito.mock(RowsFetchSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.just(new RoutingHistoryRow(
                10L,
                "req-10",
                11L,
                "session-1",
                "季度复盘",
                "agent-a",
                "chat",
                "default",
                "deepseek",
                "main",
                "deepseek-chat",
                "HIGH",
                "FAILED",
                2,
                3200,
                NOW)));

        StepVerifier.create(repository.routingHistory(
                        FROM,
                        TO,
                        "FAILED",
                        "deepseek",
                        "deepseek-chat",
                        "agent-a",
                        "default",
                        false,
                        "user-1",
                        100L,
                        20))
                .assertNext(item -> {
                    assertThat(item.requestId()).isEqualTo("req-10");
                    assertThat(item.sessionTitle()).isEqualTo("季度复盘");
                    assertThat(item.tries()).isEqualTo(2);
                })
                .verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient).sql(sql.capture());
        assertThat(sql.getValue())
                .contains("LEFT JOIN ai_conversation c ON c.id = r.conversation_id")
                .contains("c.title AS session_title")
                .contains("(:all OR r.user_id = :userId)")
                .contains("r.route_trace ->> 'tries'")
                .contains("r.id < :beforeId")
                .contains("ORDER BY r.id DESC")
                .contains("LIMIT :limit");
        verify(spec).bind(eq("fromAt"), any(OffsetDateTime.class));
        verify(spec).bind(eq("toAt"), any(OffsetDateTime.class));
        verify(spec).bind("status", "FAILED");
        verify(spec).bind("provider", "deepseek");
        verify(spec).bind("model", "deepseek-chat");
        verify(spec).bind("agent", "agent-a");
        verify(spec).bind("group", "default");
        verify(spec).bind("all", false);
        verify(spec).bind("userId", "user-1");
        verify(spec).bind("beforeId", 100L);
        verify(spec).bind("limit", 20);
    }

    @Test
    void routingHistoryMapsTheTriesColumnFromTheTrace() {
        when(row.get("id", Long.class)).thenReturn(10L);
        when(row.get("request_id", String.class)).thenReturn("req-10");
        when(row.get("conversation_id", Long.class)).thenReturn(11L);
        when(row.get("session_key", String.class)).thenReturn("session-1");
        when(row.get("session_title", String.class)).thenReturn("季度复盘");
        when(row.get("agent_id", String.class)).thenReturn("agent-a");
        when(row.get("mode_code", String.class)).thenReturn("chat");
        when(row.get("route_group", String.class)).thenReturn("default");
        when(row.get("selected_provider", String.class)).thenReturn("deepseek");
        when(row.get("selected_account", String.class)).thenReturn("main");
        when(row.get("selected_model", String.class)).thenReturn("deepseek-chat");
        when(row.get("tier", String.class)).thenReturn("HIGH");
        when(row.get("status", String.class)).thenReturn("FAILED");
        when(row.get("tries", Long.class)).thenReturn(2L);
        when(row.get("latency_ms", Integer.class)).thenReturn(3200);
        when(row.get("created_at", Instant.class)).thenReturn(NOW);

        var mapped = AiStatsRepository.mapRoutingHistory(row);

        assertThat(mapped.requestId()).isEqualTo("req-10");
        assertThat(mapped.conversationId()).isEqualTo(11L);
        assertThat(mapped.sessionTitle()).isEqualTo("季度复盘");
        assertThat(mapped.tries()).isEqualTo(2L);
        assertThat(mapped.latencyMs()).isEqualTo(3200);
        assertThat(mapped.createdAt()).isEqualTo(NOW);
    }

    @Test
    void unitStatsAndRoutingDaysBucketDaysInBeijingTime() {
        @SuppressWarnings("unchecked")
        var fetchSpec = (RowsFetchSpec<PointStatsRow>) org.mockito.Mockito.mock(RowsFetchSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(fetchSpec).when(spec).map(any(BiFunction.class));
        when(fetchSpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.units(7)).verifyComplete();
        StepVerifier.create(repository.routingDays(7)).verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient, times(2)).sql(sql.capture());
        assertThat(sql.getAllValues())
                .allSatisfy(statement -> assertThat(statement)
                        .contains("date_trunc('day', created_at AT TIME ZONE 'Asia/Shanghai')")
                        .contains("COALESCE(usage_units, points, 0)")
                        .contains("AS units")
                        .doesNotContain("AS points")
                        .contains("GROUP BY day"));
    }

    @Test
    void routingSummaryAndAccountActivityUseTheBeijingDayBoundary() {
        @SuppressWarnings("unchecked")
        var summarySpec = (RowsFetchSpec<RoutingSummaryRow>) org.mockito.Mockito.mock(RowsFetchSpec.class);
        @SuppressWarnings("unchecked")
        var activitySpec = (RowsFetchSpec<AccountActivityRow>) org.mockito.Mockito.mock(RowsFetchSpec.class);
        when(databaseClient.sql(anyString())).thenReturn(spec);
        when(spec.bind(anyString(), any())).thenReturn(spec);
        doReturn(summarySpec).when(spec).map(any(BiFunction.class));
        when(summarySpec.one()).thenReturn(Mono.empty());

        StepVerifier.create(repository.routingSummary(7)).verifyComplete();

        doReturn(activitySpec).when(spec).map(any(BiFunction.class));
        when(activitySpec.all()).thenReturn(Flux.empty());

        StepVerifier.create(repository.accountActivityToday()).verifyComplete();

        var sql = ArgumentCaptor.forClass(String.class);
        verify(databaseClient, times(2)).sql(sql.capture());
        assertThat(sql.getAllValues().getFirst())
                .contains("AS units")
                .contains("AS today_units")
                .doesNotContain("AS today_points");
        assertThat(sql.getAllValues())
                .allSatisfy(statement -> assertThat(statement)
                        .contains("(date_trunc('day', now() AT TIME ZONE 'Asia/Shanghai'))"
                                + " AT TIME ZONE 'Asia/Shanghai'"));
    }
}
