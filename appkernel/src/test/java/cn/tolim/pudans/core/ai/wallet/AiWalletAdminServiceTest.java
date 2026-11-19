package cn.tolim.pudans.core.ai.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletHoldRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository.WalletRecord;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository.WalletSnapshot;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.TxnSummary;
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
class AiWalletAdminServiceTest {

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
    void adjustsAWalletAndWritesATransaction() {
        when(txnRepository.findByIdempotencyKey("admin-adjust:adjust-1")).thenReturn(Mono.empty());
        when(walletRepository.ensure("user-1")).thenReturn(Mono.empty());
        when(walletRepository.adjust("user-1", new BigDecimal("5.00")))
                .thenReturn(Mono.just(new WalletSnapshot(new BigDecimal("15.00"), BigDecimal.ZERO)));
        when(txnRepository.insertIfAbsent(any())).thenReturn(Mono.just(true));
        when(walletRepository.findDetailByUserId("user-1")).thenReturn(Mono.just(walletRecord()));

        StepVerifier.create(service.adjust("adjust-1", "user-1", new BigDecimal("5.00")))
                .assertNext(detail -> assertThat(detail.balance()).isEqualByComparingTo("15.00"))
                .verifyComplete();
    }

    @Test
    void replaysAnAdjustmentIdempotently() {
        when(txnRepository.findByIdempotencyKey("admin-adjust:adjust-1"))
                .thenReturn(Mono.just(new TxnSummary(
                        "user-1", new BigDecimal("5.00"), new BigDecimal("15.00"), "admin-adjust:adjust-1")));
        when(walletRepository.ensure("user-1")).thenReturn(Mono.empty());
        when(walletRepository.findDetailByUserId("user-1")).thenReturn(Mono.just(walletRecord()));

        StepVerifier.create(service.adjust("adjust-1", "user-1", new BigDecimal("5.00")))
                .assertNext(detail -> assertThat(detail.balance()).isEqualByComparingTo("15.00"))
                .verifyComplete();

        verify(walletRepository, never()).adjust(any(), any());
    }

    @Test
    void freezesAWallet() {
        when(walletRepository.ensure("user-1")).thenReturn(Mono.empty());
        when(walletRepository.setStatus("user-1", 0)).thenReturn(Mono.just(true));
        when(walletRepository.findDetailByUserId("user-1"))
                .thenReturn(Mono.just(new WalletRecord(
                        new BigDecimal("15.00"),
                        BigDecimal.ZERO,
                        new BigDecimal("15.00"),
                        BigDecimal.ZERO,
                        0,
                        null)));

        StepVerifier.create(service.freezeAccount("user-1"))
                .assertNext(detail -> assertThat(detail.status()).isZero())
                .verifyComplete();
    }

    @Test
    void exportsTransactionsWithFilters() {
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2026-02-01T00:00:00Z");
        when(txnRepository.export("user-1", from, to, 100)).thenReturn(Flux.empty());

        StepVerifier.create(service.exportTransactions("user-1", from, to, 100)).verifyComplete();

        verify(txnRepository).export("user-1", from, to, 100);
    }

    private static WalletRecord walletRecord() {
        return new WalletRecord(
                new BigDecimal("15.00"),
                BigDecimal.ZERO,
                new BigDecimal("15.00"),
                BigDecimal.ZERO,
                1,
                null);
    }
}
