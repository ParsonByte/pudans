package cn.tolim.pudans.core.ai.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletHoldRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository.WalletRecord;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletTxnRecord;
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
class AiWalletServiceWalletTest {

    private static final Instant CREATED_AT = Instant.parse("2026-01-01T00:00:00Z");

    @Mock
    AiWalletRepository walletRepository;

    @Mock
    AiWalletHoldRepository holdRepository;

    @Mock
    AiWalletTxnRepository txnRepository;

    private AiWalletService service;

    @BeforeEach
    void setUp() {
        service = new AiWalletService(walletRepository, holdRepository, txnRepository);
    }

    @Test
    void createsTheWalletLazilyAndReportsBalances() {
        when(walletRepository.ensure("user-1")).thenReturn(Mono.empty());
        when(walletRepository.findDetailByUserId("user-1")).thenReturn(Mono.just(new WalletRecord(
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("200.00"),
                new BigDecimal("30.00"),
                1,
                new BigDecimal("50.00"))));

        StepVerifier.create(service.wallet("user-1"))
                .assertNext(detail -> {
                    assertThat(detail.balance()).isEqualByComparingTo("100.00");
                    assertThat(detail.frozen()).isEqualByComparingTo("10.00");
                    assertThat(detail.available()).isEqualByComparingTo("90.00");
                    assertThat(detail.totalGranted()).isEqualByComparingTo("200.00");
                    assertThat(detail.totalConsumed()).isEqualByComparingTo("30.00");
                    assertThat(detail.status()).isEqualTo(1);
                    assertThat(detail.dailyLimit()).isEqualByComparingTo("50.00");
                })
                .verifyComplete();

        verify(walletRepository).ensure("user-1");
    }

    @Test
    void reportsAMissingWallet() {
        when(walletRepository.ensure("user-1")).thenReturn(Mono.empty());
        when(walletRepository.findDetailByUserId("user-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.wallet("user-1"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-wallet-wallet-missing");
                })
                .verify();
    }

    @Test
    void listsOnlyTheCurrentUsersTransactions() {
        when(txnRepository.listByUser("user-1", 30L, 10)).thenReturn(Flux.just(transaction()));

        StepVerifier.create(service.transactions("user-1", 30L, 10))
                .assertNext(txn -> {
                    assertThat(txn.txnType()).isEqualTo("CHARGE");
                    assertThat(txn.balanceAfter()).isEqualByComparingTo("97.00");
                })
                .verifyComplete();

        verify(txnRepository).listByUser("user-1", 30L, 10);
    }

    @Test
    void rejectsAnInvalidTransactionPage() {
        assertThatThrownBy(() -> service.transactions("user-1", 0L, 10))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("流水游标");
        assertThatThrownBy(() -> service.transactions("user-1", 1L, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("流水数量");

        verifyNoInteractions(walletRepository, txnRepository);
    }

    @Test
    void rejectsAWalletLookupWithoutUserId() {
        assertThatThrownBy(() -> service.wallet(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId");

        verifyNoInteractions(walletRepository);
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
