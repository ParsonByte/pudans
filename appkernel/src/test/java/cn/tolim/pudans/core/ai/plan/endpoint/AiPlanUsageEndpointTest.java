package cn.tolim.pudans.core.ai.plan.endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.plan.PlanUsageService;
import cn.tolim.pudans.core.ai.plan.PlanVoucherService;
import cn.tolim.pudans.infra.exception.ApiException;
import java.security.Principal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiPlanUsageEndpointTest {

    private static final String USERNAME = "alice";

    private static final String USER_ID = "uid-alice";

    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final Instant STARTS_AT = Instant.parse("2026-11-18T09:00:00Z");

    private static final Instant ENDS_AT = Instant.parse("2026-11-18T14:00:00Z");

    @Mock
    PlanUsageService usageService;

    @Mock
    UserService userService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiPlanUsageEndpoint(usageService, new AiEndpointRequests(userService));
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(PRINCIPAL)).build()))
                .build();
    }

    @Test
    void returnsTheCurrentUsersUsage() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(usageService.usage(USER_ID)).thenReturn(Mono.just(view()));

        client.get()
                .uri("/aiplan/-/usage")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.plan.name").isEqualTo("standard")
                .jsonPath("$.plan.displayName").isEqualTo("套餐 standard")
                .jsonPath("$.plan.matchedBy").isEqualTo("User/" + USER_ID)
                .jsonPath("$.fiveHour.limit").isEqualTo(3000000)
                .jsonPath("$.fiveHour.used").isEqualTo(1200000)
                .jsonPath("$.fiveHour.remaining").isEqualTo(1750000)
                .jsonPath("$.fiveHour.state").isEqualTo("ACTIVE")
                .jsonPath("$.weekly.state").isEqualTo("IDLE")
                .jsonPath("$.vouchers.fiveHour.total").isEqualTo(2)
                .jsonPath("$.vouchers.weekly.remaining").isEqualTo(1);
    }

    @Test
    void returnsAUsersUsageByUid() {
        when(usageService.usage("uid-bob")).thenReturn(Mono.just(view()));

        client.get()
                .uri("/aiplan/-/usage/uid-bob")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.plan.name").isEqualTo("standard");

        verify(usageService).usage("uid-bob");
    }

    @Test
    void reportsAMissingPlan() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(usageService.usage(USER_ID)).thenReturn(Mono.error(new ApiException(
                HttpStatus.FORBIDDEN, "ai-plan-missing", "当前用户未分配 AI 套餐。")));

        client.get()
                .uri("/aiplan/-/usage")
                .exchange()
                .expectStatus().isForbidden();
    }

    @Test
    void requiresAnAuthenticatedUser() {
        var endpoint = new AiPlanUsageEndpoint(usageService, new AiEndpointRequests(userService));
        var anonymous = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

        anonymous.get()
                .uri("/aiplan/-/usage")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private static User user() {
        var user = new User();
        user.getSpec().setUid(USER_ID);
        return user;
    }

    private static PlanUsageService.UsageView view() {
        return new PlanUsageService.UsageView(
                new PlanUsageService.PlanSummary("standard", "套餐 standard", "User/" + USER_ID),
                new PlanUsageService.WindowUsage(
                        3_000_000L, 1_200_000L, 50_000L, 1_750_000L, STARTS_AT, ENDS_AT, "ACTIVE"),
                new PlanUsageService.WindowUsage(
                        20_000_000L, 0, 0, 20_000_000L, null, null, "IDLE"),
                new PlanUsageService.RateLimit(4, 120),
                new PlanVoucherService.VoucherBalance(
                        new PlanVoucherService.Balance(2, 0, 2),
                        new PlanVoucherService.Balance(1, 0, 1)));
    }
}