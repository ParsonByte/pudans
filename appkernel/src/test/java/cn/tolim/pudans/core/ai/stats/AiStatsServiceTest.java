package cn.tolim.pudans.core.ai.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.infra.r2dbc.ai.model.AiModelRequestRepository;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.AccountActivityRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.DailyRoutingRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RecentChatRequestRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.RoutingSummaryRow;
import cn.tolim.pudans.infra.r2dbc.ai.stats.AiStatsRepository.UserActivityRow;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiStatsServiceTest {

    @Mock
    AiStatsRepository repository;

    @Mock
    AiModelRequestRepository modelRequestRepository;

    @Mock
    UserService userService;

    private AiStatsService service;

    @BeforeEach
    void setUp() {
        service = new AiStatsService(repository, modelRequestRepository, userService);
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
                    assertThat(live.summary().todayPoints()).isEqualByComparingTo("0.18");
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
