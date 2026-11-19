package cn.tolim.pudans.core.ai.wallet.endpoint;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.core.ai.endpoint.AiEndpointRequests;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletTxnRecord;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiWalletEndpointTest {

    private static final String USERNAME = "alice";

    private static final String USER_ID = "uid-alice";

    private static final Principal PRINCIPAL = () -> USERNAME;

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    AiWalletService walletService;

    @Mock
    UserService userService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiWalletEndpoint(walletService, new AiEndpointRequests(userService));
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .webFilter((exchange, chain) -> chain.filter(
                        exchange.mutate().principal(Mono.just(PRINCIPAL)).build()))
                .build();
    }

    @Test
    void returnsTheCurrentUsersWallet() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(walletService.wallet(USER_ID)).thenReturn(Mono.just(new AiWalletService.WalletDetail(
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("90.00"),
                new BigDecimal("200.00"),
                new BigDecimal("30.00"),
                1,
                new BigDecimal("50.00"))));

        client.get()
                .uri("/wallet")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(100.00)
                .jsonPath("$.frozen").isEqualTo(10.00)
                .jsonPath("$.available").isEqualTo(90.00)
                .jsonPath("$.totalGranted").isEqualTo(200.00)
                .jsonPath("$.totalConsumed").isEqualTo(30.00)
                .jsonPath("$.status").isEqualTo(1)
                .jsonPath("$.dailyLimit").isEqualTo(50.00);
    }

    @Test
    void reportsAMissingWallet() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(walletService.wallet(USER_ID)).thenReturn(Mono.error(new ApiException(
                HttpStatus.NOT_FOUND, "ai-wallet-wallet-missing", "用户钱包不存在。")));

        client.get()
                .uri("/wallet")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void listsTransactionsWithDefaultPaging() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(walletService.transactions(USER_ID, Long.MAX_VALUE, 20)).thenReturn(Flux.just(transaction()));

        client.get()
                .uri("/wallet/-/transactions")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].txnType").isEqualTo("CHARGE")
                .jsonPath("$[0].amount").isEqualTo(-3.00)
                .jsonPath("$[0].balanceAfter").isEqualTo(97.00);

        verify(walletService).transactions(USER_ID, Long.MAX_VALUE, 20);
    }

    @Test
    void appliesTheTransactionCursorAndLimit() {
        when(userService.getUser(USERNAME)).thenReturn(Mono.just(user()));
        when(walletService.transactions(USER_ID, 9L, 100)).thenReturn(Flux.empty());

        client.get()
                .uri("/wallet/-/transactions?limit=100&beforeId=9")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");

        verify(walletService).transactions(USER_ID, 9L, 100);
    }

    @Test
    void rejectsAnOutOfRangeTransactionLimit() {
        client.get()
                .uri("/wallet/-/transactions?limit=0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void requiresAnAuthenticatedUser() {
        var endpoint = new AiWalletEndpoint(walletService, new AiEndpointRequests(userService));
        var anonymous = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();

        anonymous.get()
                .uri("/wallet")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    private static User user() {
        var user = new User();
        user.getSpec().setUid(USER_ID);
        return user;
    }

    private static WalletTxnRecord transaction() {
        return new WalletTxnRecord(
                21L,
                "CHARGE",
                new BigDecimal("-3.00"),
                new BigDecimal("97.00"),
                "req-1",
                "openai-primary",
                "gpt-4o-mini",
                "CHEAP",
                120,
                0,
                64,
                "trace-1",
                "REAL",
                CREATED_AT);
    }
}
