package cn.tolim.pudans.core.ai.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.stats.AiStats.RoutingHistoryQuery;
import cn.tolim.pudans.core.ai.stats.UsageAccessResolver.Viewer;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.AccountActivityRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.DailyRoutingRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.RecentChatRequestRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.RoutingHistoryRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.RoutingSummaryRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiStatsRepository.UserActivityRow;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiUsageRepository;
import cn.tolim.pudans.core.infra.r2dbc.ai.stats.AiUsageRepository.RequestDetailRow;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiStatsTest {

    private static final String USER_ID = "user-1";

    private static final Instant FROM_AT = Instant.parse("2026-11-12T16:00:00Z");

    private static final Instant TO_AT = Instant.parse("2026-11-19T16:00:00Z");

    private static final Instant NOW = Instant.parse("2026-11-19T08:00:00Z");

    private static final Viewer SELF = new Viewer(USER_ID, false, false);

    @Mock
    AiStatsRepository repository;

    @Mock
    AiUsageRepository usageRepository;

    @Mock
    AiModelRequestRepository modelRequestRepository;

    @Mock
    Accounts userService;

    private AiStats service;

    @BeforeEach
    void setUp() {
        service = new AiStats(repository, modelRequestRepository, userService, usageRepository);
        service.setClock(Clock.fixed(Instant.parse("2026-11-19T04:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void assemblesTheLiveRoutingOverview() {
        when(repository.routingSummary(7)).thenReturn(Mono.just(new RoutingSummaryRow(
                12, 2, 1, 1200, 800, 300, new BigDecimal("0.42"),
                5, 1, 500, new BigDecimal("0.18"))));
        when(repository.routingDays(7)).thenReturn(Flux.just(
                new DailyRoutingRow("2026-11-19", 5, 1, new BigDecimal("0.18")),
                new DailyRoutingRow("2026-11-18", 7, 1, new BigDecimal("0.24"))));
        when(repository.recentChatRequests(7, 20)).thenReturn(Flux.just(new RecentChatRequestRow(
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
                Instant.parse("2026-11-19T08:00:04Z"))));
        when(repository.accountActivityToday()).thenReturn(Flux.just(
                new AccountActivityRow("deepseek", "test", 5, 4, 1)));
        when(repository.userActivity(7, 20)).thenReturn(Flux.just(
                new UserActivityRow("user-1", 9, 1, Instant.parse("2026-11-19T08:10:00Z")),
                new UserActivityRow("user-2", 3, 0, Instant.parse("2026-11-19T08:05:00Z"))));
        when(userService.getUserByUid("user-1")).thenReturn(Mono.just(user("parsonbyte", "刘城霖")));
        when(userService.getUserByUid("user-2")).thenReturn(Mono.empty());

        StepVerifier.create(service.routingLive(7, 20))
                .assertNext(live -> {
                    assertThat(live.summary().requests()).isEqualTo(12);
                    assertThat(live.summary().failed()).isEqualTo(2);
                    assertThat(live.summary().todayRequests()).isEqualTo(5);
                    assertThat(live.summary().todayUnits()).isEqualByComparingTo("0.18");
                    assertThat(live.daily()).hasSize(2);
                    assertThat(live.daily().getFirst().day()).isEqualTo("2026-11-19");
                    assertThat(live.requests()).hasSize(1);
                    var request = live.requests().getFirst();
                    assertThat(request.requestId()).isEqualTo("req-1");
                    assertThat(request.provider()).isEqualTo("deepseek");
                    assertThat(request.model()).isEqualTo("deepseek-chat");
                    assertThat(request.inputTokens()).isEqualTo(900);
                    assertThat(request.createdAt()).isEqualTo(Instant.parse("2026-11-19T08:00:00Z"));
                    assertThat(live.accounts()).hasSize(1);
                    assertThat(live.accounts().getFirst().success()).isEqualTo(4);
                    assertThat(live.users()).hasSize(2);
                    var first = live.users().getFirst();
                    assertThat(first.userId()).isEqualTo("user-1");
                    assertThat(first.username()).isEqualTo("parsonbyte");
                    assertThat(first.displayName()).isEqualTo("刘城霖");
                    assertThat(first.requests()).isEqualTo(9);
                    assertThat(first.failed()).isEqualTo(1);
                    var unresolved = live.users().get(1);
                    assertThat(unresolved.userId()).isEqualTo("user-2");
                    assertThat(unresolved.username()).isNull();
                    assertThat(unresolved.displayName()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void rejectsOutOfRangeWindows() {
        assertThatThrownBy(() -> service.routingLive(0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days");
        assertThatThrownBy(() -> service.routingLive(7, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> service.routingLive(91, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days");
        assertThatThrownBy(() -> service.routingLive(7, 201))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
    }

    @Test
    void assemblesTheRoutingHistoryPageWithTheSessionTitle() {
        when(repository.routingHistory(
                        FROM_AT, TO_AT, "FAILED", "deepseek", "deepseek-chat", "agent-a", "default",
                        false, USER_ID, 100, 3))
                .thenReturn(Flux.just(
                        historyRow(10, "req-10", "季度复盘", 1),
                        historyRow(9, "req-9", null, 0),
                        historyRow(8, "req-8", "临时会话", 2)));

        StepVerifier.create(service.routingHistory(
                        new RoutingHistoryQuery(
                                null,
                                null,
                                "FAILED",
                                "deepseek",
                                "deepseek-chat",
                                "agent-a",
                                "default"),
                        100,
                        2,
                        SELF))
                .assertNext(page -> {
                    assertThat(page.items()).hasSize(2);
                    var first = page.items().getFirst();
                    assertThat(first.requestId()).isEqualTo("req-10");
                    assertThat(first.conversationId()).isEqualTo(11L);
                    assertThat(first.sessionKey()).isEqualTo("session-1");
                    assertThat(first.sessionTitle()).isEqualTo("季度复盘");
                    assertThat(first.tries()).isEqualTo(1);
                    assertThat(first.latencyMs()).isEqualTo(3200);
                    assertThat(page.items().get(1).sessionTitle()).isNull();
                    assertThat(page.nextCursor()).isEqualTo(9L);
                })
                .verifyComplete();
    }

    @Test
    void routingHistoryForwardsTheAllScopeAndValidatesPaging() {
        when(repository.routingHistory(
                        FROM_AT, TO_AT, "", "", "", "", "", true, USER_ID, 50, 11))
                .thenReturn(Flux.empty());

        StepVerifier.create(service.routingHistory(
                        new RoutingHistoryQuery(null, null, null, null, null, null, null),
                        50,
                        10,
                        new Viewer(USER_ID, true, true)))
                .assertNext(page -> {
                    assertThat(page.items()).isEmpty();
                    assertThat(page.nextCursor()).isNull();
                })
                .verifyComplete();

        assertThatThrownBy(() -> service.routingHistory(
                        new RoutingHistoryQuery(null, null, null, null, null, null, null), 50, 0, SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.LIMIT_INVALID));
        assertThatThrownBy(() -> service.routingHistory(
                        new RoutingHistoryQuery(null, null, null, null, null, null, null), 0, 10, SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getCode()).isEqualTo(AiUsageErrorCodes.CURSOR_INVALID));
        assertThatThrownBy(() -> service.routingHistory(
                        new RoutingHistoryQuery(
                                LocalDate.parse("2026-11-20"),
                                LocalDate.parse("2026-11-19"),
                                null,
                                null,
                                null,
                                null,
                                null),
                        50,
                        10,
                        SELF))
                .isInstanceOfSatisfying(ApiException.class, error ->
                        assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void routingHistoryDetailReturnsTheFullTracesForTheOwner() {
        when(usageRepository.requestDetail("req-5")).thenReturn(Mono.just(detailRow("user-1", 3)));

        StepVerifier.create(service.routingHistoryDetail("req-5", SELF))
                .assertNext(detail -> {
                    assertThat(detail.requestId()).isEqualTo("req-5");
                    assertThat(detail.sessionKey()).isEqualTo("session-1");
                    assertThat(detail.tries()).isEqualTo(3);
                    assertThat(detail.routeTrace().get("matchedRule").asText()).isEqualTo("cheap");
                    assertThat(detail.retrievalTrace().get("kbTokens").asLong()).isEqualTo(120L);
                })
                .verifyComplete();
    }

    @Test
    void routingHistoryDetailHidesForeignAndMissingRequests() {
        when(usageRepository.requestDetail("foreign")).thenReturn(Mono.just(detailRow("user-2", 1)));
        when(usageRepository.requestDetail("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.routingHistoryDetail("foreign", SELF))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception -> {
                            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                            assertThat(exception.getCode()).isEqualTo(AiUsageErrorCodes.REQUEST_NOT_FOUND);
                        }))
                .verify();
        StepVerifier.create(service.routingHistoryDetail("missing", new Viewer(USER_ID, true, true)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class, exception ->
                                assertThat(exception.getCode()).isEqualTo(AiUsageErrorCodes.REQUEST_NOT_FOUND)))
                .verify();
    }

    private static RoutingHistoryRow historyRow(
            long id, String requestId, String sessionTitle, int tries) {
        return new RoutingHistoryRow(
                id,
                requestId,
                id + 1,
                "session-1",
                sessionTitle,
                "agent-a",
                "chat",
                "default",
                "deepseek",
                "main",
                "deepseek-chat",
                "HIGH",
                "FAILED",
                tries,
                3200,
                NOW);
    }

    private static RequestDetailRow detailRow(String userId, int tries) {
        return new RequestDetailRow(
                "req-5",
                userId,
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
                "PROVIDER",
                "SETTLED",
                "PROVIDER",
                "upstream-1",
                3200,
                500,
                new BigDecimal("37.50"),
                "session-1",
                9L,
                JsonNodeFactory.instance.objectNode().put("tries", tries).put("matchedRule", "cheap"),
                JsonNodeFactory.instance.objectNode().put("kbTokens", 120),
                NOW,
                NOW);
    }

    private static User user(String username, String displayName) {
        var user = new User();
        var metadata = new Metadata();
        metadata.setName(username);
        user.setMetadata(metadata);
        user.getSpec().setUid("user-1");
        user.getSpec().setDisplayName(displayName);
        return user;
    }
}
