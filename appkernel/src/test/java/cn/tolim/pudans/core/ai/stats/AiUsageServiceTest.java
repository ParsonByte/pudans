package cn.tolim.pudans.core.ai.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiAgent;
import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.stats.AiUsageService.ClearQuery;
import cn.tolim.pudans.core.ai.stats.AiUsageService.ContextQuery;
import cn.tolim.pudans.core.ai.stats.AiUsageService.GridQuery;
import cn.tolim.pudans.core.ai.stats.AiUsageService.HeatmapQuery;
import cn.tolim.pudans.core.ai.stats.AiUsageService.OverviewQuery;
import cn.tolim.pudans.core.ai.stats.AiUsageService.RequestQuery;
import cn.tolim.pudans.core.ai.stats.AiUsageService.SessionQuery;
import cn.tolim.pudans.core.ai.stats.AiUsageService.SessionStatsQuery;
import cn.tolim.pudans.core.ai.stats.AiUsageService.SpeedQuery;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver.Viewer;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.core.ai.wallet.AiWalletService.WalletDetail;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository;
import cn.tolim.pudans.infra.r2dbc.ai.chat.AiConversationRepository.Conversation;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.ContextGridRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.ContextRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.MessageRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.OverviewDailyRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.OverviewRankingRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestDetailRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.RequestTotalsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsDailyRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsHourRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionStatsTotalsRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SessionUsageRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiUsageRepository.SpeedRow;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    private static final String USER_ID = "user-1";

    private static final Instant FROM_AT = Instant.parse("2026-11-12T16:00:00Z");

    private static final Instant TO_AT = Instant.parse("2026-11-19T16:00:00Z");

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");

    private static final Viewer SELF = new Viewer(USER_ID, false, false);

    private static final Viewer ADMIN_ALL = new Viewer(USER_ID, true, true);

    @Mock
    AiUsageRepository repository;

    @Mock
    AiConversationRepository conversationRepository;

    @Mock
    AiWalletService walletService;

    @Mock
    ReactiveExtensionClient extensionClient;

    private AiUsageService service;

    @BeforeEach
    void setUp() {
        service = new AiUsageService(repository, conversationRepository, walletService, extensionClient);
        service.setClock(Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void overviewUsesTheDefaultSevenDayWindowAndFillsTheSeries() {
        when(repository.overviewSeries(FROM_AT, TO_AT, "", "", "", "", "day", false, USER_ID))
                .thenReturn(Flux.just(
                        new OverviewDailyRow(
                                "2026-11-19", 5, 4, 1, 300, 10, 150, 460, new BigDecimal("0.30"))));
        when(repository.overviewRankings(FROM_AT, TO_AT, "", "", "", "", false, USER_ID))
                .thenReturn(Flux.just(
                        ranking("agent-a", "deepseek-chat", "deepseek", "main", 2, 1, 1, "0.10"),
                        ranking("agent-b", "qwen-max", "qwen", "main", 3, 3, 0, "0.20")));
        when(walletService.wallet(USER_ID)).thenReturn(Mono.just(wallet()));

        StepVerifier.create(service.overview(
                        new OverviewQuery(null, null, null, null, null, null, null), SELF))
                .assertNext(overview -> {
                    assertThat(overview.totals().requests()).isEqualTo(5);
                    assertThat(overview.totals().succeeded()).isEqualTo(4);
                    assertThat(overview.totals().failed()).isEqualTo(1);
                    assertThat(overview.totals().running()).isZero();
                    assertThat(overview.totals().points()).isEqualByComparingTo("0.30");
                    assertThat(overview.totals().latencyMs()).isEqualTo(500);
                    assertThat(overview.totals().ttftMs()).isEqualTo(200);
                    assertThat(overview.totals().tps()).isEqualByComparingTo("20.00");
                    assertThat(overview.totals().cacheHitRate()).isEqualByComparingTo("0.0909");
                    assertThat(overview.totals().retriedRequests()).isEqualTo(5);
                    assertThat(overview.totals().canceledRequests()).isEqualTo(5);
                    assertThat(overview.totals().unpricedRequests()).isZero();
                    assertThat(overview.totals().reasoningTokens()).isEqualTo(100);
                    assertThat(overview.series()).hasSize(7);
                    assertThat(overview.series().getFirst().day()).isEqualTo("2026-11-13");
                    assertThat(overview.series().getFirst().requests()).isZero();
                    assertThat(overview.series().getLast().day()).isEqualTo("2026-11-19");
                    assertThat(overview.series().getLast().inputTokens()).isEqualTo(300);
                    assertThat(overview.rankings().agents())
                            .extracting(entry -> entry.name())
                            .containsExactly("agent-b", "agent-a");
                    assertThat(overview.rankings().models())
                            .extracting(entry -> entry.name())
                            .containsExactly("qwen-max", "deepseek-chat");
                    assertThat(overview.rankings().providers())
                            .extracting(entry -> entry.name())
                            .containsExactly("qwen", "deepseek");
                    assertThat(overview.rankings().accounts()).hasSize(2);
                    assertThat(overview.rankings().accounts().getFirst().provider()).isEqualTo("qwen");
                    assertThat(overview.quotas().balance()).isEqualByComparingTo("12.50");
                })
                .verifyComplete();
    }

    @Test
    void overviewForwardsTheBucketAndTheAllScope() {
        when(repository.overviewSeries(FROM_AT, TO_AT, "", "", "", "", "week", true, USER_ID))
                .thenReturn(Flux.empty());
        when(repository.overviewRankings(FROM_AT, TO_AT, "", "", "", "", true, USER_ID))
                .thenReturn(Flux.empty());
        when(walletService.wallet(USER_ID)).thenReturn(Mono.just(wallet()));

        StepVerifier.create(service.overview(
                        new OverviewQuery(null, null, null, null, null, null, "week"), ADMIN_ALL))
                .assertNext(overview -> {
                    assertThat(overview.series()).hasSize(2);
                    assertThat(overview.series().getFirst().day()).isEqualTo("2026-11-09");
                    assertThat(overview.series().getLast().day()).isEqualTo("2026-11-16");
                })
                .verifyComplete();
    }

    @Test
    void rejectsAnInvertedOrOversizedDateRangeAndInvalidBucket() {
        assertThatThrownBy(() -> service.overview(
                        new OverviewQuery(
                                LocalDate.parse("2026-11-20"),
                                LocalDate.parse("2026-11-19"),
                                null,
                                null,
                                null,
                                null,
                                null),
                        SELF))
                .isInstanceOfSatisfying(ApiException.class, error -> {
                    assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.DATE_RANGE_INVALID);
                });
        assertThatThrownBy(() -> service.overview(
                        new OverviewQuery(
                                LocalDate.parse("2026-01-01"),
                                LocalDate.parse("2027-01-02"),
                                null,
                                null,
                                null,
                                null,
                                null),
                        SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.DATE_RANGE_INVALID));
        assertThatThrownBy(() -> service.overview(
                        new OverviewQuery(null, null, null, null, null, null, "minute"), SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.BUCKET_INVALID));
    }

    @Test
    void requestsValidatePagingAndAssembleTheCursor() {
        assertThatThrownBy(() -> service.requests(requestQuery(), 10, 0, SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.LIMIT_INVALID));
        assertThatThrownBy(() -> service.requests(requestQuery(), 10, 201, SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.LIMIT_INVALID));
        assertThatThrownBy(() -> service.requests(requestQuery(), 0, 50, SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.CURSOR_INVALID));

        when(repository.requests(
                        any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyBoolean(), anyBoolean(), anyString(), anyLong(), anyInt()))
                .thenReturn(Flux.just(requestRow(10, "req-10"), requestRow(9, "req-9"), requestRow(8, "req-8")));
        when(repository.requestTotals(
                        any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyBoolean(), anyBoolean(), anyString()))
                .thenReturn(Mono.just(totalsRow()));

        StepVerifier.create(service.requests(requestQuery(), 100, 2, SELF))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(2);
                    assertThat(page.items().getFirst().requestId()).isEqualTo("req-10");
                    assertThat(page.items().getFirst().cacheHitRate()).isEqualByComparingTo("0.4375");
                    assertThat(page.items().getFirst().retried()).isFalse();
                    assertThat(page.items().getFirst().servedEffort()).isEqualTo("high");
                    assertThat(page.nextCursor()).isEqualTo(9L);
                    assertThat(page.totals().requests()).isEqualTo(12);
                    assertThat(page.totals().running()).isZero();
                    assertThat(page.totals().latencyMs()).isNull();
                    assertThat(page.totals().retriedRequests()).isEqualTo(2);
                    assertThat(page.totals().reasoningTokens()).isEqualTo(40);
                })
                .verifyComplete();
    }

    @Test
    void requestsPassTheFiltersAndScopeToTheRepository() {
        when(repository.requests(
                        any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyBoolean(), anyBoolean(), anyString(), anyLong(), anyInt()))
                .thenReturn(Flux.empty());
        when(repository.requestTotals(
                        any(), any(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyString(), anyString(), anyString(), anyString(), anyString(),
                        anyString(), anyBoolean(), anyBoolean(), anyString()))
                .thenReturn(Mono.just(emptyTotalsRow()));
        var query = new RequestQuery(
                LocalDate.parse("2026-11-13"),
                LocalDate.parse("2026-11-19"),
                "FAILED",
                "deepseek",
                "deepseek-chat",
                "main",
                "agent-a",
                "gateway-agent",
                "chat",
                "default",
                "timeout",
                "agent-run",
                "gateway",
                "key-1",
                true);

        StepVerifier.create(service.requests(query, 50, 20, SELF)).expectNextCount(1).verifyComplete();

        var purpose = ArgumentCaptor.forClass(String.class);
        var via = ArgumentCaptor.forClass(String.class);
        var callerKey = ArgumentCaptor.forClass(String.class);
        verify(repository).requests(
                eq(FROM_AT),
                eq(TO_AT),
                eq("FAILED"),
                eq("deepseek"),
                eq("deepseek-chat"),
                eq("main"),
                eq("agent-a"),
                eq("gateway-agent"),
                eq("chat"),
                eq("default"),
                eq("timeout"),
                purpose.capture(),
                via.capture(),
                callerKey.capture(),
                eq(true),
                eq(false),
                eq(USER_ID),
                eq(50L),
                eq(21));
        assertThat(purpose.getValue()).isEqualTo("agent-run");
        assertThat(via.getValue()).isEqualTo("gateway");
        assertThat(callerKey.getValue()).isEqualTo("key-1");
    }

    @Test
    void exportEmitsTheExtendedColumns() {
        when(repository.requests(
                        eq(FROM_AT),
                        eq(TO_AT),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(""),
                        eq(false),
                        eq(false),
                        eq(USER_ID),
                        eq(AiUsageService.NO_CURSOR),
                        eq(10_000)))
                .thenReturn(Flux.just(exportRow()));

        StepVerifier.create(service.export(requestQuery(), SELF).collectList())
                .assertNext(lines -> {
                    assertThat(lines).hasSize(2);
                    assertThat(lines.getFirst()).isEqualTo(UsageCsvExporter.HEADER);
                    assertThat(lines.get(1))
                            .isEqualTo("2026-11-19T08:00:00Z,req-5,agent-a,deepseek,\"deepseek,chat\",main,"
                                    + "chat,default,SUCCEEDED,900,120,700,1020,0.03,3200,500,0.4375,false,high\n");
                })
                .verifyComplete();
    }

    @Test
    void requestDetailReturnsTheBillingTraceAndDerivedFields() {
        when(repository.requestDetail("req-5")).thenReturn(Mono.just(detailRow()));
        when(extensionClient.get(AiProvider.class, "deepseek")).thenReturn(Mono.just(provider()));

        StepVerifier.create(service.request("req-5", SELF))
                .assertNext(detail -> {
                    assertThat(detail.requestId()).isEqualTo("req-5");
                    assertThat(detail.billing().status()).isEqualTo("SETTLED");
                    assertThat(detail.billing().overdraft()).isEqualByComparingTo("0.10");
                    assertThat(detail.routeTrace().get("attempts").asInt()).isEqualTo(2);
                    assertThat(detail.retrievalTrace().get("kbTokens").asLong()).isEqualTo(120L);
                    assertThat(detail.ttftMs()).isEqualTo(500);
                    assertThat(detail.retried()).isTrue();
                    assertThat(detail.cacheHitRate()).isEqualByComparingTo("0.4375");
                    assertThat(detail.servedEffort()).isEqualTo("high");
                    assertThat(detail.vendor()).isEqualTo("OPENAI");
                    assertThat(detail.stream()).isTrue();
                })
                .verifyComplete();
    }

    @Test
    void requestDetailHidesOtherUsersAndMissingRequests() {
        var other = detailRow();
        var notOwned = new RequestDetailRow(
                other.requestId(),
                "user-2",
                other.teamId(),
                other.agent(),
                other.agentSource(),
                other.mode(),
                other.routeGroup(),
                other.provider(),
                other.account(),
                other.model(),
                other.tier(),
                other.status(),
                other.errorCode(),
                other.inputTokens(),
                other.cacheTokens(),
                other.outputTokens(),
                other.totalTokens(),
                other.points(),
                other.billingSource(),
                other.billingStatus(),
                other.overdraft(),
                other.usageSource(),
                other.upstreamRequestId(),
                other.latencyMs(),
                other.ttftMs(),
                other.tps(),
                other.sessionKey(),
                other.conversationId(),
                other.routeTrace(),
                other.retrievalTrace(),
                other.createdAt(),
                other.completedAt());
        when(repository.requestDetail("req-5")).thenReturn(Mono.just(notOwned));
        when(repository.requestDetail("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.request("req-5", SELF))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                            assertThat(exception.getCode()).isEqualTo(AiUsageErrorCodes.REQUEST_NOT_FOUND);
                        }))
                .verify();
        StepVerifier.create(service.request("missing", ADMIN_ALL))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception ->
                                assertThat(exception.getCode()).isEqualTo(AiUsageErrorCodes.REQUEST_NOT_FOUND)))
                .verify();
    }

    @Test
    void sessionsAggregateTheDurationAndCursor() {
        when(repository.sessions(FROM_AT, TO_AT, "", "", "", "", "", "", false, USER_ID, 100, 3))
                .thenReturn(Flux.just(sessionRow(9, "第一"), sessionRow(8, "第二"), sessionRow(7, "第三")));

        StepVerifier.create(service.sessions(new SessionQuery(null, null, null, null, null, null, null, null), 100, 2, SELF))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(2);
                    assertThat(page.items().getFirst().conversationId()).isEqualTo(9);
                    assertThat(page.items().getFirst().durationSeconds()).isEqualTo(3600);
                    assertThat(page.items().getFirst().messageCount()).isEqualTo(8);
                    assertThat(page.items().getFirst().cacheHitRate()).isEqualByComparingTo("0.2000");
                    assertThat(page.nextCursor()).isEqualTo(8L);
                })
                .verifyComplete();
    }

    @Test
    void sessionDetailCombinesTheConversationCountsAndRecentMessages() {
        when(conversationRepository.findById(9L)).thenReturn(Mono.just(conversation()));
        when(repository.sessionUsage(9L)).thenReturn(Mono.just(sessionUsage()));
        when(repository.recentMessages(9L, 10)).thenReturn(Flux.just(
                new MessageRow(12L, "assistant", "最近回复", 120, "deepseek-chat", NOW),
                new MessageRow(11L, "user", "最近提问", null, null, NOW.minusSeconds(30))));

        StepVerifier.create(service.session(9, SELF))
                .assertNext(detail -> {
                    assertThat(detail.conversation().conversationId()).isEqualTo(9);
                    assertThat(detail.conversation().title()).isEqualTo("季度复盘");
                    assertThat(detail.conversation().agentSource()).isEqualTo("pudans-chat");
                    assertThat(detail.conversation().durationSeconds()).isEqualTo(3600);
                    assertThat(detail.messageCount()).isEqualTo(8);
                    assertThat(detail.userMessageCount()).isEqualTo(4);
                    assertThat(detail.assistantMessageCount()).isEqualTo(4);
                    assertThat(detail.recentMessages()).hasSize(2);
                    assertThat(detail.recentMessages().getFirst().content()).isEqualTo("最近回复");
                })
                .verifyComplete();
    }

    @Test
    void sessionDetailRejectsInvalidMissingAndForeignConversations() {
        assertThatThrownBy(() -> service.session(0, SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.CONVERSATION_INVALID));

        when(conversationRepository.findById(404L)).thenReturn(Mono.empty());
        when(conversationRepository.findById(9L)).thenReturn(Mono.just(foreignConversation()));

        StepVerifier.create(service.session(404, SELF))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                            assertThat(exception.getCode())
                                    .isEqualTo(AiUsageErrorCodes.SESSION_NOT_FOUND);
                        }))
                .verify();
        StepVerifier.create(service.session(9, SELF))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception ->
                                assertThat(exception.getCode()).isEqualTo(AiUsageErrorCodes.SESSION_NOT_FOUND)))
                .verify();
    }

    @Test
    void contextEstimatesTheCompositionAndWindowUtilization() {
        when(repository.contextRows(FROM_AT, TO_AT, "", false, USER_ID))
                .thenReturn(Flux.just(new ContextRow("agent-a", "deepseek-chat", 2, 1000, 200, 80, 20, 0, 0)));
        when(extensionClient.listAll(eq(AiModel.class), any(), any()))
                .thenReturn(Flux.just(model()));
        when(extensionClient.listAll(eq(AiAgent.class), any(), any()))
                .thenReturn(Flux.just(agent()));

        StepVerifier.create(service.context(new ContextQuery(null, null, null), SELF))
                .assertNext(context -> {
                    assertThat(context.totals().requests()).isEqualTo(2);
                    assertThat(context.totals().userTokens()).isEqualTo(35);
                    assertThat(context.totals().systemTokens()).isEqualTo(8);
                    assertThat(context.totals().historyTokens()).isEqualTo(957);
                    assertThat(context.totals().memoryTokens()).isZero();
                    assertThat(context.totals().kbTokens()).isZero();
                    assertThat(context.totals().contextWindowTokens()).isEqualTo(256_000L);
                    assertThat(context.totals().windowUtilization())
                            .isEqualByComparingTo("0.0039");
                    assertThat(context.agents()).hasSize(1);
                    var breakdown = context.agents().getFirst();
                    assertThat(breakdown.name()).isEqualTo("agent-a");
                    assertThat(breakdown.contextWindow()).isNull();
                    assertThat(breakdown.contextWindowTokens()).isEqualTo(256_000L);
                    assertThat(context.models()).hasSize(1);
                    var model = context.models().getFirst();
                    assertThat(model.name()).isEqualTo("deepseek-chat");
                    assertThat(model.contextWindow()).isEqualTo(128_000);
                })
                .verifyComplete();
    }

    @Test
    void heatmapFillsTheDailyBucketsAndValidatesTheMetric() {
        when(repository.overviewSeries(FROM_AT, TO_AT, "", "", "", "", "day", false, USER_ID))
                .thenReturn(Flux.just(
                        new OverviewDailyRow(
                                "2026-11-19", 5, 4, 1, 300, 10, 150, 460, new BigDecimal("0.30"))));

        StepVerifier.create(service.heatmap(new HeatmapQuery(null, null, "tokens"), SELF))
                .assertNext(heatmap -> {
                    assertThat(heatmap.metric()).isEqualTo("tokens");
                    assertThat(heatmap.start()).isEqualTo("2026-11-13");
                    assertThat(heatmap.end()).isEqualTo("2026-11-19");
                    assertThat(heatmap.buckets()).hasSize(7);
                    assertThat(heatmap.buckets().getLast().date()).isEqualTo("2026-11-19");
                    assertThat(heatmap.buckets().getLast().tokens()).isEqualTo(460);
                    assertThat(heatmap.buckets().getFirst().requests()).isZero();
                })
                .verifyComplete();

        assertThatThrownBy(() -> service.heatmap(new HeatmapQuery(null, null, "cost"), SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.METRIC_INVALID));
    }

    @Test
    void speedMapsTheLatencyPercentiles() {
        when(repository.speed(FROM_AT, TO_AT, "model", false, USER_ID))
                .thenReturn(Flux.just(
                        new SpeedRow("deepseek-chat", 10, 1, 210.4, 198.6, 32.456, 30.1)));

        StepVerifier.create(service.speed(new SpeedQuery(null, null, "model"), SELF))
                .assertNext(speed -> {
                    assertThat(speed.items()).hasSize(1);
                    var item = speed.items().getFirst();
                    assertThat(item.name()).isEqualTo("deepseek-chat");
                    assertThat(item.requests()).isEqualTo(10);
                    assertThat(item.failures()).isEqualTo(1);
                    assertThat(item.avgTtftMs()).isEqualTo(210);
                    assertThat(item.p50TtftMs()).isEqualTo(199);
                    assertThat(item.avgTps()).isEqualByComparingTo("32.46");
                    assertThat(item.p50Tps()).isEqualByComparingTo("30.10");
                })
                .verifyComplete();

        assertThatThrownBy(() -> service.speed(new SpeedQuery(null, null, "account"), SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.GROUP_BY_INVALID));
    }

    @Test
    void sessionStatsAssemblesTotalsSeriesModelsAndShape() {
        when(repository.sessionStatsTotals(FROM_AT, TO_AT, "", "", false, USER_ID))
                .thenReturn(Mono.just(new SessionStatsTotalsRow(
                        2, 8.4, 12.6, 2000, new BigDecimal("0.50"), 300, 180)));
        when(repository.sessionStatsSessions(FROM_AT, TO_AT, "", "", false, USER_ID))
                .thenReturn(Flux.just(
                        new SessionStatsRow(
                                1L, "短会话", "agent-a", "model-a", 5, 2, 600,
                                new BigDecimal("0.10"), 100, NOW),
                        new SessionStatsRow(
                                2L, "长会话", "agent-a", null, 120, 30, 1400,
                                new BigDecimal("0.40"), 200, NOW.minusSeconds(60))));
        when(repository.sessionStatsDaily(FROM_AT, TO_AT, "", "", false, USER_ID))
                .thenReturn(Flux.just(new SessionStatsDailyRow(
                        "2026-11-19", 2, 125, 2000, new BigDecimal("0.50"), 180)));
        when(repository.sessionStatsHours(FROM_AT, TO_AT, "", "", false, USER_ID))
                .thenReturn(Flux.just(new SessionStatsHourRow(9, 2), new SessionStatsHourRow(10, 1)));

        StepVerifier.create(service.sessionStats(new SessionStatsQuery(null, null, null, null), SELF))
                .assertNext(stats -> {
                    assertThat(stats.totals().sessions()).isEqualTo(2);
                    assertThat(stats.totals().medianMessages()).isEqualTo(8);
                    assertThat(stats.totals().p90Messages()).isEqualTo(13);
                    assertThat(stats.totals().tokens()).isEqualTo(2000);
                    assertThat(stats.totals().cacheReadTokens()).isEqualTo(300);
                    assertThat(stats.totals().activeSeconds()).isEqualTo(180);
                    assertThat(stats.series()).hasSize(1);
                    assertThat(stats.series().getFirst().messages()).isEqualTo(125);
                    assertThat(stats.hours()).hasSize(24);
                    assertThat(stats.hours().get(9).sessions()).isEqualTo(2);
                    assertThat(stats.hours().get(10).sessions()).isEqualTo(1);
                    assertThat(stats.hours().getFirst().sessions()).isZero();
                    assertThat(stats.models())
                            .extracting(model -> model.name())
                            .containsExactly("unknown", "model-a");
                    assertThat(stats.topSessions()).hasSize(2);
                    assertThat(stats.topSessions().getFirst().conversationId()).isEqualTo(2L);
                    assertThat(stats.shape())
                            .extracting(shape -> shape.bucket())
                            .containsExactly("0", "1-9", "10-19", "20-49", "50-99", "100+");
                    assertThat(stats.shape().get(1).messages()).isEqualTo(5);
                    assertThat(stats.shape().getLast().messages()).isEqualTo(120);
                })
                .verifyComplete();
    }

    @Test
    void contextGridMapsTheRequestParts() {
        when(repository.contextGrid(FROM_AT, TO_AT, "", "", false, USER_ID, 200))
                .thenReturn(Flux.just(new ContextGridRow(
                        "req-1", "agent-a", "deepseek-chat", NOW, 1000, 200, 20, 30, 40, 0)));
        when(extensionClient.listAll(eq(AiModel.class), any(), any()))
                .thenReturn(Flux.just(model()));
        when(extensionClient.listAll(eq(AiAgent.class), any(), any()))
                .thenReturn(Flux.just(agent()));

        StepVerifier.create(service.contextGrid(new GridQuery(null, null, null, null, null), SELF))
                .assertNext(grid -> {
                    assertThat(grid.items()).hasSize(1);
                    var item = grid.items().getFirst();
                    assertThat(item.requestId()).isEqualTo("req-1");
                    assertThat(item.contextWindow()).isEqualTo(128_000);
                    assertThat(item.utilization()).isEqualByComparingTo("0.0078");
                    assertThat(item.parts().system()).isEqualTo(4);
                    assertThat(item.parts().user()).isEqualTo(10);
                    assertThat(item.parts().memory()).isEqualTo(20);
                    assertThat(item.parts().kb()).isEqualTo(30);
                    assertThat(item.parts().history()).isEqualTo(936);
                    assertThat(item.parts().output()).isEqualTo(200);
                })
                .verifyComplete();

        assertThatThrownBy(() -> service.contextGrid(new GridQuery(null, null, null, null, 0), SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.LIMIT_INVALID));
    }

    @Test
    void clearRequiresAnAdministratorAndABeforeDate() {
        var before = Instant.parse("2026-10-31T16:00:00Z");
        when(repository.deleteChatRequests(before, "deepseek", "deepseek-chat"))
                .thenReturn(Mono.just(3L));
        when(repository.deleteModelRequests(before, "deepseek", "deepseek-chat"))
                .thenReturn(Mono.just(1L));

        StepVerifier.create(service.clear(
                        new ClearQuery(LocalDate.parse("2026-11-01"), "deepseek", "deepseek-chat"),
                        ADMIN_ALL))
                .assertNext(result -> {
                    assertThat(result.requestsDeleted()).isEqualTo(3);
                    assertThat(result.modelRequestsDeleted()).isEqualTo(1);
                })
                .verifyComplete();

        StepVerifier.create(service.clear(new ClearQuery(LocalDate.parse("2026-11-01"), null, null), SELF))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                            assertThat(exception.getCode()).isEqualTo(AiUsageErrorCodes.FORBIDDEN);
                        }))
                .verify();
        StepVerifier.create(service.clear(new ClearQuery(null, null, null), ADMIN_ALL))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception ->
                                assertThat(exception.getCode())
                                        .isEqualTo(AiUsageErrorCodes.BEFORE_REQUIRED)))
                .verify();
    }

    private static RequestQuery requestQuery() {
        return new RequestQuery(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static OverviewRankingRow ranking(
            String agent,
            String model,
            String provider,
            String account,
            long requests,
            long succeeded,
            long failed,
            String points) {
        return new OverviewRankingRow(
                agent,
                model,
                provider,
                account,
                requests,
                succeeded,
                failed,
                requests,
                0,
                requests,
                20 * requests,
                100 * requests,
                10 * requests,
                50 * requests,
                160 * requests,
                new BigDecimal(points),
                1000 * requests / 2,
                requests,
                400 * requests / 2,
                requests,
                new BigDecimal("20.00").multiply(BigDecimal.valueOf(requests)),
                requests);
    }

    private static RequestTotalsRow totalsRow() {
        return new RequestTotalsRow(
                12, 10, 2, 0, 0, 2, 40, 900, 100, 400, 1400, new BigDecimal("0.60"));
    }

    private static RequestTotalsRow emptyTotalsRow() {
        return new RequestTotalsRow(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO);
    }

    private static RequestRow requestRow(long id, String requestId) {
        return new RequestRow(
                id,
                requestId,
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
                new BigDecimal("0.03"),
                3200,
                500,
                new BigDecimal("37.50"),
                "session-1",
                9L,
                false,
                "high",
                NOW,
                NOW);
    }

    private static RequestRow exportRow() {
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
                "deepseek,chat",
                "HIGH",
                "SUCCEEDED",
                null,
                900,
                700,
                120,
                1020,
                new BigDecimal("0.03"),
                3200,
                500,
                new BigDecimal("37.50"),
                "session-1",
                9L,
                false,
                null,
                NOW,
                NOW);
    }

    private static RequestDetailRow detailRow() {
        return new RequestDetailRow(
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
                new BigDecimal("0.03"),
                "WALLET",
                "SETTLED",
                new BigDecimal("0.10"),
                "PROVIDER",
                "upstream-1",
                3200,
                500,
                new BigDecimal("37.50"),
                "session-1",
                9L,
                JsonNodeFactory.instance
                        .objectNode()
                        .put("attempts", 2)
                        .put("tries", 3)
                        .put("servedEffort", "high")
                        .put("stream", true),
                JsonNodeFactory.instance.objectNode().put("kbTokens", 120),
                NOW,
                NOW);
    }

    private static SessionRow sessionRow(long conversationId, String title) {
        return new SessionRow(
                conversationId,
                title,
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

    private static SessionUsageRow sessionUsage() {
        return new SessionUsageRow(
                4, 1200, 300, 800, 2300, new BigDecimal("0.42"), "deepseek-chat", 8, 4, 4);
    }

    private static Conversation conversation() {
        return new Conversation(
                9L,
                "default",
                "user-1",
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                "季度复盘",
                "chat",
                "default",
                "ACTIVE",
                null,
                null,
                0,
                0,
                NOW,
                NOW.minusSeconds(3600),
                NOW);
    }

    private static Conversation foreignConversation() {
        return new Conversation(
                9L,
                "default",
                "user-2",
                "team-a",
                "agent-a",
                "pudans-chat",
                "session-1",
                null,
                "季度复盘",
                "chat",
                "default",
                "ACTIVE",
                null,
                null,
                0,
                0,
                NOW,
                NOW.minusSeconds(3600),
                NOW);
    }

    private static WalletDetail wallet() {
        return new WalletDetail(
                new BigDecimal("12.50"),
                new BigDecimal("1.00"),
                new BigDecimal("11.50"),
                new BigDecimal("20.00"),
                new BigDecimal("7.50"),
                1,
                new BigDecimal("5.00"));
    }

    private static AiModel model() {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName("deepseek-chat-model");
        model.setMetadata(metadata);
        model.getSpec().setModelCode("deepseek-chat");
        model.getSpec().setContextWindow(128_000);
        return model;
    }

    private static AiAgent agent() {
        var agent = new AiAgent();
        var metadata = new Metadata();
        metadata.setName("agent-a");
        agent.setMetadata(metadata);
        agent.getSpec().setPrompt("系统提示");
        return agent;
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName("deepseek");
        provider.setMetadata(metadata);
        provider.getSpec().setVendor(AiProvider.Vendor.OPENAI);
        return provider;
    }
}