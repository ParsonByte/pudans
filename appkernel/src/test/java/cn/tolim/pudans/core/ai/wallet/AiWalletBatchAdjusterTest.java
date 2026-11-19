package cn.tolim.pudans.core.ai.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiWalletBatchAdjusterTest {

    @Mock
    AiWalletService walletService;

    private AiWalletBatchAdjuster adjuster;

    @BeforeEach
    void setUp() {
        adjuster = new AiWalletBatchAdjuster(walletService);
    }

    @Test
    void parsesOptionalHeaderAndSkipsBlankLines() {
        when(walletService.adjust("req-1", "user-1", new BigDecimal("5.00")))
                .thenReturn(Mono.just(detail("15.00")));
        when(walletService.adjust("req-2", "user-2", new BigDecimal("-2.50")))
                .thenReturn(Mono.just(detail("7.50")));
        var csv = "userId,amount,requestId\n\nuser-1,5.00,req-1\n\nuser-2,-2.50,req-2\n";

        StepVerifier.create(adjuster.adjust("batch-1", csv))
                .assertNext(result -> {
                    assertThat(result.batchId()).isEqualTo("batch-1");
                    assertThat(result.total()).isEqualTo(2);
                    assertThat(result.succeeded()).isEqualTo(2);
                    assertThat(result.failed()).isZero();
                    assertThat(result.rows()).hasSize(2);
                    assertThat(result.rows().get(0).row()).isEqualTo(1);
                    assertThat(result.rows().get(0).balance()).isEqualByComparingTo("15.00");
                    assertThat(result.rows().get(1).amount()).isEqualByComparingTo("-2.50");
                })
                .verifyComplete();
    }

    @Test
    void stripsUtf8BomBeforeTheHeader() {
        when(walletService.adjust("req-1", "user-1", new BigDecimal("5.00")))
                .thenReturn(Mono.just(detail("15.00")));
        var csv = "\uFEFFuserId,amount,requestId\nuser-1,5.00,req-1\n";

        StepVerifier.create(adjuster.adjust("batch-bom", csv))
                .assertNext(result -> {
                    assertThat(result.total()).isEqualTo(1);
                    assertThat(result.rows().get(0).userId()).isEqualTo("user-1");
                })
                .verifyComplete();
    }

    @Test
    void treatsFirstLineAsDataWhenHeaderIsMissing() {
        when(walletService.adjust(anyString(), eq("user-1"), any()))
                .thenReturn(Mono.just(detail("15.00")));

        StepVerifier.create(adjuster.adjust("batch-2", "user-1,5.00,\n"))
                .assertNext(result -> {
                    assertThat(result.total()).isEqualTo(1);
                    assertThat(result.rows().get(0).userId()).isEqualTo("user-1");
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(String.class);
        verify(walletService).adjust(captor.capture(), eq("user-1"), any());
        assertThat(captor.getValue()).startsWith("batch:");
    }

    @Test
    void reusesTheProvidedRequestId() {
        when(walletService.adjust("req-explicit", "user-1", new BigDecimal("5.00")))
                .thenReturn(Mono.just(detail("15.00")));

        StepVerifier.create(adjuster.adjust("batch-3", "user-1,5.00,req-explicit\n"))
                .assertNext(result -> assertThat(result.rows().get(0).status()).isEqualTo("SUCCESS"))
                .verifyComplete();

        verify(walletService).adjust("req-explicit", "user-1", new BigDecimal("5.00"));
    }

    @Test
    void derivesAStableIdempotencyKeyForRowsWithoutRequestId() {
        when(walletService.adjust(anyString(), eq("user-1"), any()))
                .thenReturn(Mono.just(detail("15.00")));

        StepVerifier.create(adjuster.adjust("batch-4", "user-1,5.00,\n"))
                .expectNextCount(1)
                .verifyComplete();
        StepVerifier.create(adjuster.adjust("batch-4", "user-1,5.00,\n"))
                .expectNextCount(1)
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(String.class);
        verify(walletService, times(2)).adjust(captor.capture(), eq("user-1"), any());
        assertThat(captor.getAllValues().get(0)).startsWith("batch:").hasSize(70);
        assertThat(captor.getAllValues().get(1)).isEqualTo(captor.getAllValues().get(0));
    }

    @Test
    void rejectsOversizedBatches() {
        var csv = new StringBuilder("userId,amount,requestId\n");
        for (var index = 1; index <= AiWalletBatchAdjuster.MAX_ROWS + 1; index++) {
            csv.append("user-").append(index).append(",1.00,req-").append(index).append('\n');
        }

        StepVerifier.create(adjuster.adjust("batch-large", csv.toString()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-wallet-batch-invalid");
                })
                .verify();

        verifyNoInteractions(walletService);
    }

    @Test
    void rejectsAnEmptyCsv() {
        StepVerifier.create(adjuster.adjust("batch-empty", ""))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("ai-wallet-batch-invalid"))
                .verify();
        StepVerifier.create(adjuster.adjust("batch-blank", "  \n \n"))
                .expectErrorSatisfies(error -> assertThat(((ApiException) error).getCode())
                        .isEqualTo("ai-wallet-batch-invalid"))
                .verify();

        verifyNoInteractions(walletService);
    }

    @Test
    void rejectsUnclosedCsvQuotes() {
        StepVerifier.create(adjuster.adjust("batch-quote", "user-1,5.00,\"req-1\n"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-wallet-batch-invalid");
                })
                .verify();

        verifyNoInteractions(walletService);
    }

    @Test
    void acceptsHeaderOnlyCsv() {
        StepVerifier.create(adjuster.adjust("batch-header", "userId,amount,requestId\n"))
                .assertNext(result -> {
                    assertThat(result.total()).isZero();
                    assertThat(result.succeeded()).isZero();
                    assertThat(result.failed()).isZero();
                    assertThat(result.rows()).isEmpty();
                })
                .verifyComplete();

        verifyNoInteractions(walletService);
    }

    @Test
    void reportsMalformedRowsWithoutFailingTheBatch() {
        when(walletService.adjust("req-ok", "user-1", new BigDecimal("5.00")))
                .thenReturn(Mono.just(detail("15.00")));
        var csv = "user-1,5.00,req-ok\n"
                + ",2.00,req-blank\n"
                + "user-2,abc,req-bad\n"
                + "user-3,0.00,req-zero\n"
                + "user-4,1.00,req-extra,oops\n";

        StepVerifier.create(adjuster.adjust("batch-mixed", csv))
                .assertNext(result -> {
                    assertThat(result.total()).isEqualTo(5);
                    assertThat(result.succeeded()).isEqualTo(1);
                    assertThat(result.failed()).isEqualTo(4);
                    assertThat(result.rows().get(0).status()).isEqualTo("SUCCESS");
                    assertThat(result.rows().get(1).errorCode()).isEqualTo("ai-wallet-batch-invalid");
                    assertThat(result.rows().get(2).errorCode()).isEqualTo("ai-wallet-amount-invalid");
                    assertThat(result.rows().get(3).errorCode()).isEqualTo("ai-wallet-amount-invalid");
                    assertThat(result.rows().get(4).errorCode()).isEqualTo("ai-wallet-batch-invalid");
                    assertThat(result.rows().get(4).amount()).isNull();
                })
                .verifyComplete();
    }

    @Test
    void returnsAFailedRowWhenAdjustIsRejected() {
        when(walletService.adjust("req-a", "user-1", new BigDecimal("1.00")))
                .thenReturn(Mono.just(detail("11.00")));
        when(walletService.adjust("req-b", "user-2", new BigDecimal("2.00")))
                .thenReturn(Mono.error(
                        new ApiException(HttpStatus.CONFLICT, "ai-wallet-adjust-conflict", "调整失败。")));

        StepVerifier.create(adjuster.adjust("batch-partial", "user-1,1.00,req-a\nuser-2,2.00,req-b\n"))
                .assertNext(result -> {
                    assertThat(result.succeeded()).isEqualTo(1);
                    assertThat(result.failed()).isEqualTo(1);
                    assertThat(result.rows().get(0).balance()).isEqualByComparingTo("11.00");
                    assertThat(result.rows().get(1).status()).isEqualTo("FAILED");
                    assertThat(result.rows().get(1).balance()).isNull();
                    assertThat(result.rows().get(1).errorCode()).isEqualTo("ai-wallet-adjust-conflict");
                    assertThat(result.rows().get(1).errorMessage()).isEqualTo("调整失败。");
                })
                .verifyComplete();
    }

    @Test
    void supportsNegativeAmounts() {
        when(walletService.adjust("req-neg", "user-1", new BigDecimal("-2.50")))
                .thenReturn(Mono.just(detail("7.50")));

        StepVerifier.create(adjuster.adjust("batch-neg", "user-1,-2.50,req-neg\n"))
                .assertNext(result -> {
                    assertThat(result.rows().get(0).status()).isEqualTo("SUCCESS");
                    assertThat(result.rows().get(0).amount()).isEqualByComparingTo("-2.50");
                    assertThat(result.rows().get(0).balance()).isEqualByComparingTo("7.50");
                })
                .verifyComplete();
    }

    @Test
    void parsesQuotedCsvFields() {
        when(walletService.adjust("req\"1", "user,1", new BigDecimal("2.00")))
                .thenReturn(Mono.just(detail("12.00")));

        StepVerifier.create(adjuster.adjust("batch-quoted", "\"user,1\",2.00,\"req\"\"1\"\n"))
                .assertNext(result -> {
                    assertThat(result.rows().get(0).userId()).isEqualTo("user,1");
                    assertThat(result.rows().get(0).status()).isEqualTo("SUCCESS");
                })
                .verifyComplete();

        verify(walletService).adjust("req\"1", "user,1", new BigDecimal("2.00"));
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
}
