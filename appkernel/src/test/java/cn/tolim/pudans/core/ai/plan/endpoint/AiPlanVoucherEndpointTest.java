package cn.tolim.pudans.core.ai.plan.endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.plan.PlanVoucherKind;
import cn.tolim.pudans.core.ai.plan.PlanVoucherService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiPlanVoucherEndpointTest {

    private static final String USERNAME = "alice";

    private static final String USER_ID = "uid-alice";

    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final Instant USED_AT = Instant.parse("2026-11-18T10:00:00Z");

    @Mock
    PlanVoucherService voucherService;

    @Mock
    UserService userService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiPlanVoucherEndpoint(voucherService, new AiEndpointRequests(userService));
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(PRINCIPAL)).build()))
                .build();
    }

    @Test
    void returnsTheCurrentUsersVoucherBalance() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(voucherService.balance(USER_ID))
                .thenReturn(Mono.just(new PlanVoucherService.VoucherBalance(
                        new PlanVoucherService.Balance(3, 1, 2),
                        new PlanVoucherService.Balance(1, 0, 1))));

        client.get()
                .uri("/aiplan/-/vouchers")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.fiveHour.total").isEqualTo(3)
                .jsonPath("$.fiveHour.used").isEqualTo(1)
                .jsonPath("$.fiveHour.remaining").isEqualTo(2)
                .jsonPath("$.weekly.remaining").isEqualTo(1);
    }

    @Test
    void consumesAVoucherForTheCurrentUser() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(voucherService.use("req-1", USER_ID, PlanVoucherKind.FIVE_HOUR_RESET))
                .thenReturn(Mono.just(new PlanVoucherService.VoucherUseResult(
                        "req-1",
                        PlanVoucherKind.FIVE_HOUR_RESET,
                        false,
                        "5h:2026-11-18T08:00:00Z",
                        "5h:2026-11-18T10:00:00Z",
                        USED_AT)));

        client.post()
                .uri("/aiplan/-/vouchers/use")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AiPlanVoucherEndpoint.UseRequest(
                        "req-1", PlanVoucherKind.FIVE_HOUR_RESET))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.requestId").isEqualTo("req-1")
                .jsonPath("$.kind").isEqualTo("FIVE_HOUR_RESET")
                .jsonPath("$.duplicate").isEqualTo(false)
                .jsonPath("$.windowKeyAfter").isEqualTo("5h:2026-11-18T10:00:00Z");

        verify(voucherService).use("req-1", USER_ID, PlanVoucherKind.FIVE_HOUR_RESET);
    }

    @Test
    void rejectsAVoucherUseWithoutRequiredFields() {
        client.post()
                .uri("/aiplan/-/vouchers/use")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AiPlanVoucherEndpoint.UseRequest(null, null))
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void grantsVouchersToUsersAndGroups() {
        when(voucherService.grant(
                        PlanVoucherKind.WEEKLY_RESET, 2, List.of(USER_ID), List.of("rd")))
                .thenReturn(Mono.just(new PlanVoucherService.GrantResult(
                        PlanVoucherKind.WEEKLY_RESET, 2, List.of(USER_ID))));

        client.post()
                .uri("/aiplan/-/vouchers/grant")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new AiPlanVoucherEndpoint.GrantRequest(
                        PlanVoucherKind.WEEKLY_RESET, 2, List.of(USER_ID), List.of("rd")))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.count").isEqualTo(2)
                .jsonPath("$.userIds[0]").isEqualTo(USER_ID);

        verify(voucherService)
                .grant(PlanVoucherKind.WEEKLY_RESET, 2, List.of(USER_ID), List.of("rd"));
    }

    @Test
    void listsHistoryWithDefaultPaging() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(voucherService.history(USER_ID, Long.MAX_VALUE, 20))
                .thenReturn(Flux.just(new PlanVoucherService.VoucherUseRecord(
                        7L,
                        "req-1",
                        "FIVE_HOUR_RESET",
                        "5h:2026-11-18T08:00:00Z",
                        "5h:2026-11-18T10:00:00Z",
                        USED_AT)));

        client.get()
                .uri("/aiplan/-/vouchers/-/history")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].id").isEqualTo(7)
                .jsonPath("$[0].kind").isEqualTo("FIVE_HOUR_RESET");

        verify(voucherService).history(USER_ID, Long.MAX_VALUE, 20);
    }

    @Test
    void appliesTheHistoryCursorAndLimit() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(voucherService.history(USER_ID, 9L, 100)).thenReturn(Flux.empty());

        client.get()
                .uri("/aiplan/-/vouchers/-/history?limit=100&beforeId=9")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");

        verify(voucherService).history(USER_ID, 9L, 100);
    }

    @Test
    void rejectsAnOutOfRangeHistoryLimit() {
        client.get()
                .uri("/aiplan/-/vouchers/-/history?limit=0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void requiresAnAuthenticatedUser() {
        var endpoint = new AiPlanVoucherEndpoint(voucherService, new AiEndpointRequests(userService));
        var anonymous = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

        anonymous.get()
                .uri("/aiplan/-/vouchers")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private static User user() {
        var user = new User();
        user.getSpec().setUid(USER_ID);
        return user;
    }
}