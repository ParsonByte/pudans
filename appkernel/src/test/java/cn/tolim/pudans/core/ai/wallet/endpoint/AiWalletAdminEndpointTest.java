package cn.tolim.pudans.core.ai.wallet.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.wallet.AiWalletBatchAdjuster;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository.WalletAdminRecord;
import java.math.BigDecimal;
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
class AiWalletAdminEndpointTest {

    @Mock
    AiWalletService walletService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(
                        new AiWalletAdminEndpoint(walletService, new AiWalletBatchAdjuster(walletService)).endpoint())
                .build();
    }

    @Test
    void listsWallets() {
        when(walletService.wallets(anyLong(), anyInt()))
                .thenReturn(Flux.just(new WalletAdminRecord(
                        1L, "user-1", new BigDecimal("10.00"), BigDecimal.ZERO,
                        new BigDecimal("10.00"), BigDecimal.ZERO, 1, null)));

        client.get()
                .uri("/wallets")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].userId").isEqualTo("user-1")
                .jsonPath("$[0].balance").isEqualTo(10.0);
    }

    @Test
    void adjustsAWallet() {
        when(walletService.adjust(anyString(), anyString(), any()))
                .thenReturn(Mono.just(detail()));

        client.post()
                .uri("/wallets/user-1/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"amount\":5.00,\"requestId\":\"adjust-1\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.balance").isEqualTo(15.0);
    }

    @Test
    void freezesAWallet() {
        when(walletService.freezeAccount("user-1")).thenReturn(Mono.just(detail()));

        client.post()
                .uri("/wallets/user-1/freeze")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo(0);
    }

    private static AiWalletService.WalletDetail detail() {
        return new AiWalletService.WalletDetail(
                new BigDecimal("15.00"),
                BigDecimal.ZERO,
                new BigDecimal("15.00"),
                new BigDecimal("15.00"),
                BigDecimal.ZERO,
                0,
                null);
    }
}
