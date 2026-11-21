package cn.tolim.pudans.core.ai.stats.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.resource.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.stats.AiUsageErrorCodes;
import cn.tolim.pudans.core.ai.stats.AiUsage;
import cn.tolim.pudans.core.ai.stats.AiUsage.Billing;
import cn.tolim.pudans.core.ai.stats.AiUsage.ClearQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.ClearResult;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextBreakdown;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextGrid;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextGridItem;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextParts;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextTotals;
import cn.tolim.pudans.core.ai.stats.AiUsage.ContextUsage;
import cn.tolim.pudans.core.ai.stats.AiUsage.DailyUsage;
import cn.tolim.pudans.core.ai.stats.AiUsage.Heatmap;
import cn.tolim.pudans.core.ai.stats.AiUsage.HeatmapBucket;
import cn.tolim.pudans.core.ai.stats.AiUsage.HeatmapQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.Overview;
import cn.tolim.pudans.core.ai.stats.AiUsage.OverviewQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.RankingEntry;
import cn.tolim.pudans.core.ai.stats.AiUsage.Rankings;
import cn.tolim.pudans.core.ai.stats.AiUsage.RecentMessage;
import cn.tolim.pudans.core.ai.stats.AiUsage.RequestDetail;
import cn.tolim.pudans.core.ai.stats.AiUsage.RequestPage;
import cn.tolim.pudans.core.ai.stats.AiUsage.RequestQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionDetail;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionModel;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionPage;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionShape;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionStats;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionStatsDay;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionStatsHour;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionStatsQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.SessionTotals;
import cn.tolim.pudans.core.ai.stats.AiUsage.Speed;
import cn.tolim.pudans.core.ai.stats.AiUsage.SpeedItem;
import cn.tolim.pudans.core.ai.stats.AiUsage.SpeedQuery;
import cn.tolim.pudans.core.ai.stats.AiUsage.TopSession;
import cn.tolim.pudans.core.ai.stats.AiUsage.UsageRequest;
import cn.tolim.pudans.core.ai.stats.AiUsage.UsageSession;
import cn.tolim.pudans.core.ai.stats.AiUsage.UsageTotals;
import cn.tolim.pudans.core.ai.plan.PlanUsage;
import cn.tolim.pudans.core.ai.plan.PlanUsage.PlanSummary;
import cn.tolim.pudans.core.ai.plan.PlanUsage.UsageView;
import cn.tolim.pudans.core.ai.plan.PlanUsage.WindowUsage;
import cn.tolim.pudans.core.ai.plan.PlanVouchers;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver.Viewer;
import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.infra.config.JacksonAdapterModule;
import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.server.WebFilter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiUsageEndpointTest {

    private static final String USERNAME = "alice";

    private static final String USER_ID = "uid-alice";

    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");

    @Mock
    AiUsage aiUsage;

    @Mock
    Accounts accounts;

    @Mock
    AuditLog auditLog;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiUsageEndpoint(
                aiUsage, new UsageAccessResolver(new AiEndpointRequests(accounts)), auditLog);
        var mapper = JsonMapper.builder()
                .addModule(new JacksonAdapterModule(JsonUtils::mapper))
                .build();
        var strategies = HandlerStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .jacksonJsonEncoder(new JacksonJsonEncoder(mapper)))
                .build();
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .handlerStrategies(strategies)
                .webFilter(authenticatedAs("ROLE_super-role"))
                .build();
    }

    @Test
    void returnsTheUsageOverviewWithTheBucketAndScope() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.overview(any(OverviewQuery.class), any(Viewer.class)))
                .thenReturn(Mono.just(overview()));

        client.get()
                .uri("/aistats/-/usage/overview?from=2026-11-13&to=2026-11-19&agent=agent-a"
                        + "&bucket=week&scope=all")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totals.requests").isEqualTo(5)
                .jsonPath("$.totals.latencyMs").isEqualTo(500)
                .jsonPath("$.totals.cacheHitRate").isEqualTo(0.02)
                .jsonPath("$.series[0].day").isEqualTo("2026-11-13")
                .jsonPath("$.rankings.agents[0].name").isEqualTo("agent-a")
                .jsonPath("$.plan.plan.name").isEqualTo("standard")
                .jsonPath("$.plan.fiveHour.remaining").isEqualTo(2999900);

        var query = ArgumentCaptor.forClass(OverviewQuery.class);
        var viewer = ArgumentCaptor.forClass(Viewer.class);
        verify(aiUsage).overview(query.capture(), viewer.capture());
        assertThat(query.getValue().from()).isEqualTo(LocalDate.parse("2026-11-13"));
        assertThat(query.getValue().to()).isEqualTo(LocalDate.parse("2026-11-19"));
        assertThat(query.getValue().agent()).isEqualTo("agent-a");
        assertThat(query.getValue().bucket()).isEqualTo("week");
        assertThat(viewer.getValue().userId()).isEqualTo(USER_ID);
        assertThat(viewer.getValue().all()).isTrue();
    }

    @Test
    void defaultsTheScopeToSelfForAdministrators() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.overview(any(OverviewQuery.class), any(Viewer.class)))
                .thenReturn(Mono.just(overview()));

        client.get().uri("/aistats/-/usage/overview").exchange().expectStatus().isOk();

        var viewer = ArgumentCaptor.forClass(Viewer.class);
        verify(aiUsage).overview(any(OverviewQuery.class), viewer.capture());
        assertThat(viewer.getValue().all()).isFalse();
        assertThat(viewer.getValue().admin()).isTrue();
    }

    @Test
    void rejectsAnUnknownScope() {
        client.get()
                .uri("/aistats/-/usage/overview?scope=team")
                .exchange()
                .expectStatus().isBadRequest();

        verify(aiUsage, never()).overview(any(), any());
    }

    @Test
    void returnsTheHeatmapAndSpeedAggregates() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.heatmap(any(HeatmapQuery.class), any(Viewer.class)))
                .thenReturn(Mono.just(new Heatmap(
                        "tokens",
                        "2026-11-13",
                        "2026-11-19",
                        List.of(new HeatmapBucket(
                                "2026-11-19", null, 5, 460, new BigDecimal("0.30"))))));
        when(aiUsage.speed(any(SpeedQuery.class), any(Viewer.class)))
                .thenReturn(Mono.just(new Speed(List.of(new SpeedItem(
                        "deepseek-chat",
                        null,
                        10,
                        1,
                        210,
                        199,
                        new BigDecimal("32.46"),
                        new BigDecimal("30.10"))))));

        client.get()
                .uri("/aistats/-/usage/heatmap?metric=tokens")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metric").isEqualTo("tokens")
                .jsonPath("$.buckets[0].tokens").isEqualTo(460);
        client.get()
                .uri("/aistats/-/usage/speed?groupBy=model")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].name").isEqualTo("deepseek-chat")
                .jsonPath("$.items[0].p50TtftMs").isEqualTo(199)
                .jsonPath("$.items[0].avgTps").isEqualTo(32.46);
    }

    @Test
    void requestsUseTheDefaultPagingAndNewFilters() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.requests(any(RequestQuery.class), eq(AiUsage.NO_CURSOR), eq(50), any()))
                .thenReturn(Mono.just(requestPage()));

        client.get()
                .uri("/aistats/-/usage/requests?status=SUCCEEDED&q=timeout&purpose=agent-run"
                        + "&agentSource=gateway-agent&via=gateway&callerKey=key-1&retried=true")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].requestId").isEqualTo("req-5")
                .jsonPath("$.items[0].agent").isEqualTo("agent-a")
                .jsonPath("$.items[0].agentSource").isEqualTo("claude-code")
                .jsonPath("$.items[0].cacheHitRate").isEqualTo(0.4375)
                .jsonPath("$.items[0].retried").isEqualTo(false)
                .jsonPath("$.items[0].servedEffort").isEqualTo("high")
                .jsonPath("$.nextCursor").isEqualTo(9)
                .jsonPath("$.totals.requests").isEqualTo(5)
                .jsonPath("$.totals.retriedRequests").isEqualTo(2);

        var query = ArgumentCaptor.forClass(RequestQuery.class);
        verify(aiUsage).requests(query.capture(), eq(AiUsage.NO_CURSOR), eq(50), any());
        assertThat(query.getValue().status()).isEqualTo("SUCCEEDED");
        assertThat(query.getValue().q()).isEqualTo("timeout");
        assertThat(query.getValue().purpose()).isEqualTo("agent-run");
        assertThat(query.getValue().agentSource()).isEqualTo("gateway-agent");
        assertThat(query.getValue().via()).isEqualTo("gateway");
        assertThat(query.getValue().callerKey()).isEqualTo("key-1");
        assertThat(query.getValue().retried()).isTrue();
    }

    @Test
    void requestsApplyTheCursorAndLimit() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.requests(any(RequestQuery.class), eq(42L), eq(5), any()))
                .thenReturn(Mono.just(requestPage()));

        client.get()
                .uri("/aistats/-/usage/requests?beforeId=42&limit=5")
                .exchange()
                .expectStatus().isOk();

        verify(aiUsage).requests(any(RequestQuery.class), eq(42L), eq(5), any());
    }

    @Test
    void rejectsAnInvalidLimitAndDate() {
        client.get()
                .uri("/aistats/-/usage/requests?limit=abc")
                .exchange()
                .expectStatus().isBadRequest();

        client.get()
                .uri("/aistats/-/usage/overview?from=2026-11-19x")
                .exchange()
                .expectStatus().isBadRequest();

        verify(aiUsage, never()).requests(any(), anyLong(), anyInt(), any());
        verify(aiUsage, never()).overview(any(), any());
    }

    @Test
    void requiresAuthentication() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.empty());

        client.get()
                .uri("/aistats/-/usage/overview")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    void exportsTheFilteredRequestsAsCsv() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.export(any(RequestQuery.class), any(Viewer.class)))
                .thenReturn(Flux.just("time,requestId\n", "2026-11-19T08:00:00Z,req-5\n"));

        client.get()
                .uri("/aistats/-/usage/requests/-/export?provider=deepseek&agentSource=gateway-agent&retried=true")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.parseMediaType("text/csv"))
                .expectHeader()
                .valueMatches("Content-Disposition", "attachment; filename=\"ai-usage-requests.csv\"")
                .expectBody(String.class)
                .isEqualTo("time,requestId\n2026-11-19T08:00:00Z,req-5\n");

        var query = ArgumentCaptor.forClass(RequestQuery.class);
        verify(aiUsage).export(query.capture(), any(Viewer.class));
        assertThat(query.getValue().provider()).isEqualTo("deepseek");
        assertThat(query.getValue().agentSource()).isEqualTo("gateway-agent");
        assertThat(query.getValue().retried()).isTrue();
    }

    @Test
    void returnsTheRequestDetail() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.request(eq("req-5"), any(Viewer.class))).thenReturn(Mono.just(requestDetail()));

        client.get()
                .uri("/aistats/-/usage/requests/req-5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("req-5")
                .jsonPath("$.billing.status").isEqualTo("SETTLED")
                .jsonPath("$.routeTrace.attempts").isEqualTo(2)
                .jsonPath("$.retrievalTrace.kbTokens").isEqualTo(120)
                .jsonPath("$.ttftMs").isEqualTo(500)
                .jsonPath("$.retried").isEqualTo(true)
                .jsonPath("$.cacheHitRate").isEqualTo(0.4375)
                .jsonPath("$.vendor").isEqualTo("OPENAI")
                .jsonPath("$.stream").isEqualTo(true);
    }

    @Test
    void returnsSessionPagesDetailsAndStats() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.sessions(any(), eq(7L), eq(20), any())).thenReturn(Mono.just(sessionPage()));
        when(aiUsage.session(eq(9L), any())).thenReturn(Mono.just(sessionDetail()));
        when(aiUsage.sessionStats(any(SessionStatsQuery.class), any()))
                .thenReturn(Mono.just(sessionStats()));

        client.get()
                .uri("/aistats/-/usage/sessions?beforeId=7&limit=20&q=复盘&model=deepseek-chat"
                        + "&status=ACTIVE&routeGroup=default")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].conversationId").isEqualTo(9)
                .jsonPath("$.items[0].messageCount").isEqualTo(8)
                .jsonPath("$.items[0].cacheHitRate").isEqualTo(0.2)
                .jsonPath("$.nextCursor").isEqualTo(8);

        client.get()
                .uri("/aistats/-/usage/sessions/9")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.conversation.conversationId").isEqualTo(9)
                .jsonPath("$.messageCount").isEqualTo(8)
                .jsonPath("$.recentMessages[0].content").isEqualTo("最近回复");

        client.get()
                .uri("/aistats/-/usage/sessions/stats?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totals.sessions").isEqualTo(2)
                .jsonPath("$.totals.medianMessages").isEqualTo(8)
                .jsonPath("$.hours[0].sessions").isEqualTo(2)
                .jsonPath("$.hours[1].sessions").isEqualTo(1)
                .jsonPath("$.models[0].name").isEqualTo("model-a")
                .jsonPath("$.topSessions[0].conversationId").isEqualTo(2)
                .jsonPath("$.shape[0].bucket").isEqualTo("1-9");
    }

    @Test
    void returnsTheContextCompositionAndGrid() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.context(any(ContextQuery.class), any())).thenReturn(Mono.just(context()));
        when(aiUsage.contextGrid(any(), any())).thenReturn(Mono.just(contextGrid()));

        client.get()
                .uri("/aistats/-/usage/context?agent=agent-a")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.totals.systemTokens").isEqualTo(8)
                .jsonPath("$.totals.windowUtilization").isEqualTo(0.0039)
                .jsonPath("$.agents[0].name").isEqualTo("agent-a")
                .jsonPath("$.models[0].contextWindow").isEqualTo(128000);

        client.get()
                .uri("/aistats/-/usage/context/grid?limit=400")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].requestId").isEqualTo("req-1")
                .jsonPath("$.items[0].parts.history").isEqualTo(936)
                .jsonPath("$.items[0].contextWindow").isEqualTo(128000);
    }

    @Test
    void clearsUsageAndWritesTheAuditRecord() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.clear(any(ClearQuery.class), any(Viewer.class)))
                .thenReturn(Mono.just(new ClearResult(3, 1)));

        client.post()
                .uri("/aistats/-/usage/-/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"before\":\"2026-11-01\",\"provider\":\"deepseek\",\"model\":\"deepseek-chat\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestsDeleted").isEqualTo(3)
                .jsonPath("$.modelRequestsDeleted").isEqualTo(1);

        var query = ArgumentCaptor.forClass(ClearQuery.class);
        verify(aiUsage).clear(query.capture(), any(Viewer.class));
        assertThat(query.getValue().before()).isEqualTo(LocalDate.parse("2026-11-01"));
        assertThat(query.getValue().provider()).isEqualTo("deepseek");
        assertThat(query.getValue().model()).isEqualTo("deepseek-chat");

        var context = ArgumentCaptor.forClass(Map.class);
        verify(auditLog).record(
                any(),
                eq("AI_USAGE_CLEAR"),
                eq("AiUsage"),
                eq("usage"),
                eq("SUCCESS"),
                eq(0L),
                context.capture());
        assertThat(context.getValue())
                .containsEntry("operatorId", USER_ID)
                .containsEntry("requestsDeleted", 3L)
                .containsEntry("modelRequestsDeleted", 1L);
    }

    @Test
    void rejectsAClearWithoutABeforeDate() {
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(aiUsage.clear(any(ClearQuery.class), any(Viewer.class)))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST, AiUsageErrorCodes.BEFORE_REQUIRED, "before 不能为空。")));

        client.post()
                .uri("/aistats/-/usage/-/clear")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest();

        var query = ArgumentCaptor.forClass(ClearQuery.class);
        verify(aiUsage).clear(query.capture(), any(Viewer.class));
        assertThat(query.getValue().before()).isNull();
    }

    private static WebFilter authenticatedAs(String... authorities) {
        var grantedAuthorities = Arrays.stream(authorities)
                .map(SimpleGrantedAuthority::new)
                .toList();
        var authentication =
                UsernamePasswordAuthenticationToken.authenticated(USERNAME, "n/a", grantedAuthorities);
        return (exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(PRINCIPAL)).build())
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
    }

    private static User user() {
        var user = new User();
        user.getSpec().setUid(USER_ID);
        return user;
    }

    private static Overview overview() {
        return new Overview(
                totals(),
                List.of(new DailyUsage(
                        "2026-11-13", 5, 4, 1, 300, 10, 150, 460, new BigDecimal("0.30"))),
                new Rankings(
                        List.of(new RankingEntry(
                                "agent-a", null, 5, 1, 300, 10, 150, new BigDecimal("0.30"))),
                        List.of(new RankingEntry(
                                "deepseek-chat", null, 5, 1, 300, 10, 150, new BigDecimal("0.30"))),
                        List.of(new RankingEntry(
                                "deepseek", null, 5, 1, 300, 10, 150, new BigDecimal("0.30"))),
                        List.of(new RankingEntry(
                                "main", "deepseek", 5, 1, 300, 10, 150, new BigDecimal("0.30")))),
                new UsageView(
                        new PlanSummary("standard", "标准套餐", "DefaultPlan"),
                        new WindowUsage(
                                3_000_000,
                                100,
                                0,
                                2_999_900,
                                Instant.parse("2026-11-19T04:00:00Z"),
                                Instant.parse("2026-11-19T09:00:00Z"),
                                "ACTIVE"),
                        new WindowUsage(
                                20_000_000,
                                100,
                                0,
                                19_999_900,
                                Instant.parse("2026-11-16T16:00:00Z"),
                                Instant.parse("2026-11-23T16:00:00Z"),
                                "ACTIVE"),
                        new PlanUsage.RateLimit(4, 120),
                        new PlanVouchers.VoucherBalance(
                                new PlanVouchers.Balance(2, 1, 1),
                                new PlanVouchers.Balance(1, 0, 1))));
    }

    private static UsageTotals totals() {
        return new UsageTotals(
                5,
                4,
                1,
                0,
                300,
                10,
                150,
                460,
                new BigDecimal("0.30"),
                500,
                200,
                new BigDecimal("20.00"),
                new BigDecimal("0.02"),
                2,
                0,
                0,
                20);
    }

    private static RequestPage requestPage() {
        return new RequestPage(List.of(request()), 9L, totals());
    }

    private static UsageRequest request() {
        return new UsageRequest(
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
                NOW,
                NOW,
                new BigDecimal("0.4375"),
                false,
                "high");
    }

    private static RequestDetail requestDetail() {
        return new RequestDetail(
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
                new Billing("PROVIDER", "SETTLED", "PROVIDER", "upstream-1"),
                3200,
                500,
                new BigDecimal("37.50"),
                "session-1",
                9L,
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                        .objectNode()
                        .put("attempts", 2),
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                        .objectNode()
                        .put("kbTokens", 120),
                NOW,
                NOW,
                true,
                new BigDecimal("0.4375"),
                "high",
                "OPENAI",
                true);
    }

    private static SessionPage sessionPage() {
        return new SessionPage(List.of(session()), 8L);
    }

    private static SessionDetail sessionDetail() {
        return new SessionDetail(
                session(),
                8,
                4,
                4,
                List.of(new RecentMessage(12L, "assistant", "最近回复", 120, "deepseek-chat", NOW)));
    }

    private static UsageSession session() {
        return new UsageSession(
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
                3600,
                NOW.minusSeconds(3600),
                NOW,
                new BigDecimal("0.2000"));
    }

    private static SessionStats sessionStats() {
        return new SessionStats(
                new SessionTotals(2, 8, 13, 2000, new BigDecimal("0.50"), 300, 180),
                List.of(new SessionStatsDay(
                        "2026-11-19", 2, 125, 2000, new BigDecimal("0.50"), 180)),
                List.of(new SessionStatsHour(9, 2), new SessionStatsHour(10, 1)),
                List.of(new SessionModel("model-a", 1, 600, new BigDecimal("0.10"))),
                List.of(new TopSession(
                        2L, "长会话", "agent-a", null, 120, 1400, new BigDecimal("0.40"), NOW)),
                List.of(new SessionShape("1-9", 5)));
    }

    private static ContextUsage context() {
        return new ContextUsage(
                new ContextTotals(2, 1000, 200, 8, 957, 0, 0, 35, 256_000L, new BigDecimal("0.0039")),
                List.of(new ContextBreakdown(
                        "agent-a",
                        null,
                        2,
                        1000,
                        200,
                        8,
                        957,
                        0,
                        0,
                        35,
                        256_000L,
                        new BigDecimal("0.0039"))),
                List.of(new ContextBreakdown(
                        "deepseek-chat",
                        128_000,
                        2,
                        1000,
                        200,
                        8,
                        957,
                        0,
                        0,
                        35,
                        256_000L,
                        new BigDecimal("0.0039"))));
    }

    private static ContextGrid contextGrid() {
        return new ContextGrid(List.of(new ContextGridItem(
                "req-1",
                "agent-a",
                "deepseek-chat",
                NOW,
                1000,
                200,
                128_000,
                new BigDecimal("0.0078"),
                new ContextParts(4, 936, 20, 30, 10, 200))));
    }
}