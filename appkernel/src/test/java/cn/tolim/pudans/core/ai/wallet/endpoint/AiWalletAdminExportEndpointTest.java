package cn.tolim.pudans.core.ai.wallet.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.wallet.AiWalletBatchAdjuster;
import cn.tolim.pudans.core.ai.wallet.AiWalletService;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository.WalletAdminRecord;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletAdminTxnRecord;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiWalletAdminExportEndpointTest {

    private static final String POINT_TXN_HEADER =
            "id,user_id,txn_type,amount,balance_after,request_id,provider_account,model_code,tier,"
                    + "input_tokens,cache_tokens,output_tokens,billing_source,created_at\n";

    private static final String WALLET_HEADER =
            "user_id,balance,frozen,available,total_granted,total_consumed,status,daily_limit\n";

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
    void batchAdjustReturnsPerRowResults() {
        when(walletService.adjust("req-1", "user-1", new BigDecimal("5.00")))
                .thenReturn(Mono.just(detail("15.00")));

        client.post()
                .uri("/wallets/-/batch-adjust")
                .contentType(MediaType.parseMediaType("text/csv"))
                .bodyValue("userId,amount,requestId\nuser-1,5.00,req-1\n")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(1)
                .jsonPath("$.succeeded").isEqualTo(1)
                .jsonPath("$.failed").isEqualTo(0)
                .jsonPath("$.rows[0].row").isEqualTo(1)
                .jsonPath("$.rows[0].userId").isEqualTo("user-1")
                .jsonPath("$.rows[0].status").isEqualTo("SUCCESS")
                .jsonPath("$.rows[0].balance").isEqualTo(15.0);
    }

    @Test
    void batchAdjustUsesTheIdempotencyHeaderAsBatchId() {
        client.post()
                .uri("/wallets/-/batch-adjust")
                .contentType(MediaType.parseMediaType("text/csv"))
                .header("Idempotency-Key", "batch-header-1")
                .bodyValue("userId,amount,requestId\n")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.batchId").isEqualTo("batch-header-1");
    }

    @Test
    void batchAdjustGeneratesABatchIdWhenTheHeaderIsMissing() {
        client.post()
                .uri("/wallets/-/batch-adjust")
                .contentType(MediaType.parseMediaType("text/csv"))
                .bodyValue("userId,amount,requestId\n")
                .exchange()
                .expectStatus().isOk()
                .expectBody(AiWalletBatchAdjuster.BatchAdjustResult.class)
                .value(result -> assertThat(result.batchId()).isNotBlank());
    }

    @Test
    void batchAdjustReturnsPartialFailuresWithOkStatus() {
        when(walletService.adjust("req-1", "user-1", new BigDecimal("5.00")))
                .thenReturn(Mono.just(detail("15.00")));

        client.post()
                .uri("/wallets/-/batch-adjust")
                .contentType(MediaType.parseMediaType("text/csv"))
                .bodyValue("user-1,5.00,req-1\nuser-2,not-a-number,req-2\n")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.total").isEqualTo(2)
                .jsonPath("$.succeeded").isEqualTo(1)
                .jsonPath("$.failed").isEqualTo(1)
                .jsonPath("$.rows[1].status").isEqualTo("FAILED")
                .jsonPath("$.rows[1].errorCode").isEqualTo("ai-wallet-amount-invalid");
    }

    @Test
    void batchAdjustRejectsOversizedCsv() {
        var csv = new StringBuilder("userId,amount,requestId\n");
        for (var index = 1; index <= AiWalletBatchAdjuster.MAX_ROWS + 1; index++) {
            csv.append("user-").append(index).append(",1.00,\n");
        }

        client.post()
                .uri("/wallets/-/batch-adjust")
                .contentType(MediaType.parseMediaType("text/csv"))
                .bodyValue(csv.toString())
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void pointTransactionsExportWritesCsvToTheResponse() {
        when(walletService.exportTransactions(null, null, null, 5000))
                .thenReturn(Flux.just(transaction("acme")));

        client.get()
                .uri("/pointtransactions/-/export")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .expectHeader().valueEquals(
                        HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"point-transactions.csv\"")
                .expectBody(String.class)
                .isEqualTo(POINT_TXN_HEADER
                        + "1,user-1,GRANT,5.00,15.00,req-1,acme,model-1,pro,10,2,20,gateway,"
                        + "2026-01-01T00:00:00Z\n");
    }

    @Test
    void pointTransactionsExportAppliesFilters() {
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");
        when(walletService.exportTransactions("user-1", from, to, 10)).thenReturn(Flux.empty());

        client.get()
                .uri("/pointtransactions/-/export?userId=user-1&from=2026-01-01T00:00:00Z"
                        + "&to=2026-02-01T00:00:00Z&limit=10")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(POINT_TXN_HEADER);

        verify(walletService).exportTransactions("user-1", from, to, 10);
    }

    @Test
    void pointTransactionsExportEscapesCsvFields() {
        when(walletService.exportTransactions(null, null, null, 5000))
                .thenReturn(Flux.just(transaction("acme, \"pro\"")));

        client.get()
                .uri("/pointtransactions/-/export")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .isEqualTo(POINT_TXN_HEADER
                        + "1,user-1,GRANT,5.00,15.00,req-1,\"acme, \"\"pro\"\"\",model-1,pro,10,2,20,"
                        + "gateway,2026-01-01T00:00:00Z\n");
    }

    @Test
    void pointTransactionsExportRejectsInvalidLimit() {
        client.get()
                .uri("/pointtransactions/-/export?limit=0")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void pointTransactionsExportRejectsInvalidInstant() {
        client.get()
                .uri("/pointtransactions/-/export?from=not-a-time")
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void walletExportWritesCsvToTheResponse() {
        when(walletService.wallets(Long.MAX_VALUE, 5000)).thenReturn(Flux.just(
                new WalletAdminRecord(
                        1L,
                        "user-1",
                        new BigDecimal("10.00"),
                        new BigDecimal("2.00"),
                        new BigDecimal("10.00"),
                        new BigDecimal("0.00"),
                        1,
                        new BigDecimal("100.00")),
                new WalletAdminRecord(
                        2L,
                        "user-2",
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        new BigDecimal("0.00"),
                        0,
                        null)));

        client.get()
                .uri("/wallets/export")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .expectHeader().valueEquals(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"wallets.csv\"")
                .expectBody(String.class)
                .isEqualTo(WALLET_HEADER
                        + "user-1,10.00,2.00,8.00,10.00,0.00,1,100.00\n"
                        + "user-2,0.00,0.00,0.00,0.00,0.00,0,\n");
    }

    @Test
    void walletExportRejectsOversizedLimit() {
        client.get()
                .uri("/wallets/export?limit=10001")
                .exchange()
                .expectStatus().isBadRequest();
    }

    private static AiWalletService.WalletDetail detail(String balance) {
        return new AiWalletService.WalletDetail(
                new BigDecimal(balance),
                new BigDecimal("0.00"),
                new BigDecimal(balance),
                new BigDecimal(balance),
                new BigDecimal("0.00"),
                1,
                null);
    }

    private static WalletAdminTxnRecord transaction(String providerAccount) {
        return new WalletAdminTxnRecord(
                1L,
                "user-1",
                "GRANT",
                new BigDecimal("5.00"),
                new BigDecimal("15.00"),
                "req-1",
                providerAccount,
                "model-1",
                "pro",
                10,
                2,
                20,
                "trace-1",
                "admin-adjust:req-1",
                "gateway",
                Instant.parse("2026-01-01T00:00:00Z"));
    }
}
