package cn.tolim.pudans.core.ai.stats.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.stats.AiStatsService;
import cn.tolim.pudans.core.ai.stats.AiStatsService.AccountActivity;
import cn.tolim.pudans.core.ai.stats.AiStatsService.DailyRouting;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RecentRequest;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RoutingHistoryDetail;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RoutingHistoryItem;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RoutingHistoryPage;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RoutingHistoryQuery;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RoutingLive;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RoutingUser;
import cn.tolim.pudans.core.ai.stats.AiStatsService.Summary;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver.Viewer;
import cn.tolim.pudans.infra.config.JacksonAdapterModule;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.json.JacksonJsonEncoder;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class AiStatsEndpointTest {

    private static final String USERNAME = "alice";

    private static final String USER_ID = "uid-alice";

    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");

    @Mock
    AiStatsService statsService;

    @Mock
    UserService userService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiStatsEndpoint(
                statsService, new UsageAccessResolver(new AiEndpointRequests(userService)));
        var mapper = JsonMapper.builder()
                .addModule(new JacksonAdapterModule(JsonUtils::mapper))
                .build();
        var strategies = HandlerStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs()
                        .jacksonJsonEncoder(new JacksonJsonEncoder(mapper)))
                .build();
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .handlerStrategies(strategies)
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(PRINCIPAL)).build()))
                .build();
    }

    @Test
    void returnsTheLiveRoutingOverview() {
        when(statsService.routingLive(7, 5)).thenReturn(Mono.just(overview()));

        client.get()
                .uri("/aistats/-/routing/live?days=7&limit=5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.summary.requests").isEqualTo(12)
                .jsonPath("$.summary.todayRequests").isEqualTo(5)
                .jsonPath("$.daily[0].day").isEqualTo("2026-11-19")
                .jsonPath("$.requests[0].requestId").isEqualTo("req-1")
                .jsonPath("$.requests[0].provider").isEqualTo("deepseek")
                .jsonPath("$.requests[0].status").isEqualTo("SUCCEEDED")
                .jsonPath("$.accounts[0].account").isEqualTo("test")
                .jsonPath("$.users[0].displayName").isEqualTo("刘城霖")
                .jsonPath("$.users[0].requests").isEqualTo(9);
    }

    @Test
    void appliesTheDefaultWindowAndLimit() {
        when(statsService.routingLive(14, 20)).thenReturn(Mono.just(overview()));

        client.get()
                .uri("/aistats/-/routing/live")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.summary.requests").isEqualTo(12);
    }

    @Test
    void rejectsAnOutOfRangeLimit() {
        client.get()
                .uri("/aistats/-/routing/live?limit=0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void returnsTheRoutingHistoryPage() {
        authenticatedUser();
        when(statsService.routingHistory(
                        any(RoutingHistoryQuery.class), eq(100L), eq(20), any(Viewer.class)))
                .thenReturn(Mono.just(new RoutingHistoryPage(
                        List.of(historyItem()), 9L)));

        client.get()
                .uri("/aistats/-/routing/history?from=2026-11-13&to=2026-11-19&status=FAILED"
                        + "&provider=deepseek&model=deepseek-chat&agent=agent-a&group=default"
                        + "&beforeId=100&limit=20&scope=all")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.items[0].requestId").isEqualTo("req-10")
                .jsonPath("$.items[0].sessionTitle").isEqualTo("季度复盘")
                .jsonPath("$.items[0].tries").isEqualTo(2)
                .jsonPath("$.items[0].latencyMs").isEqualTo(3200)
                .jsonPath("$.nextCursor").isEqualTo(9);

        var query = ArgumentCaptor.forClass(RoutingHistoryQuery.class);
        var viewer = ArgumentCaptor.forClass(Viewer.class);
        verify(statsService).routingHistory(query.capture(), eq(100L), eq(20), viewer.capture());
        assertThat(query.getValue().from()).isEqualTo(LocalDate.parse("2026-11-13"));
        assertThat(query.getValue().to()).isEqualTo(LocalDate.parse("2026-11-19"));
        assertThat(query.getValue().status()).isEqualTo("FAILED");
        assertThat(query.getValue().provider()).isEqualTo("deepseek");
        assertThat(query.getValue().model()).isEqualTo("deepseek-chat");
        assertThat(query.getValue().agent()).isEqualTo("agent-a");
        assertThat(query.getValue().group()).isEqualTo("default");
        assertThat(viewer.getValue().userId()).isEqualTo(USER_ID);
        assertThat(viewer.getValue().all()).isFalse();
    }

    @Test
    void returnsTheRoutingHistoryDetail() {
        authenticatedUser();
        when(statsService.routingHistoryDetail(eq("req-5"), any(Viewer.class)))
                .thenReturn(Mono.just(historyDetail()));

        client.get()
                .uri("/aistats/-/routing/history/req-5")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("req-5")
                .jsonPath("$.tries").isEqualTo(2)
                .jsonPath("$.routeTrace.matchedRule").isEqualTo("cheap")
                .jsonPath("$.retrievalTrace.kbTokens").isEqualTo(120);
    }

    @Test
    void rejectsAnUnknownScope() {
        client.get()
                .uri("/aistats/-/routing/history?scope=team")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private void authenticatedUser() {
        var user = new User();
        user.getSpec().setUid(USER_ID);
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user));
    }

    private static RoutingHistoryItem historyItem() {
        return new RoutingHistoryItem(
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
                NOW);
    }

    private static RoutingHistoryDetail historyDetail() {
        return new RoutingHistoryDetail(
                "req-5",
                9L,
                "session-1",
                "agent-a",
                "chat",
                "default",
                "deepseek",
                "main",
                "deepseek-chat",
                "HIGH",
                "SUCCEEDED",
                2,
                3200,
                NOW,
                NOW,
                JsonNodeFactory.instance.objectNode().put("matchedRule", "cheap"),
                JsonNodeFactory.instance.objectNode().put("kbTokens", 120));
    }

    private static RoutingLive overview() {
        return new RoutingLive(
                new Summary(
                        12,
                        2,
                        1,
                        1200,
                        800,
                        300,
                        new BigDecimal("0.42"),
                        5,
                        1,
                        500,
                        new BigDecimal("0.18")),
                List.of(new DailyRouting("2026-11-19", 5, 1, new BigDecimal("0.18"))),
                List.of(new RecentRequest(
                        "req-1",
                        "user-1",
                        "claude-code",
                        "default",
                        "deepseek",
                        "test",
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
                        Instant.parse("2026-11-19T08:00:00Z"),
                        Instant.parse("2026-11-19T08:00:04Z"))),
                List.of(new AccountActivity("deepseek", "test", 5, 4, 1)),
                List.of(new RoutingUser(
                        "user-1",
                        "parsonbyte",
                        "刘城霖",
                        9,
                        1,
                        Instant.parse("2026-11-19T08:10:00Z"))));
    }
}