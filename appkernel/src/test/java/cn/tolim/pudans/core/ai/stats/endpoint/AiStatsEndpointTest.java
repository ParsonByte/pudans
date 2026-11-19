package cn.tolim.pudans.core.ai.stats.endpoint;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.stats.AiStatsService;
import cn.tolim.pudans.core.ai.stats.AiStatsService.AccountActivity;
import cn.tolim.pudans.core.ai.stats.AiStatsService.DailyRouting;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RecentRequest;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RoutingLive;
import cn.tolim.pudans.core.ai.stats.AiStatsService.RoutingUser;
import cn.tolim.pudans.core.ai.stats.AiStatsService.Summary;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiStatsEndpointTest {

    @Mock
    AiStatsService statsService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(new AiStatsEndpoint(statsService).endpoint())
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
