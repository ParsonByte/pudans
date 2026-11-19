package cn.tolim.pudans.core.ai.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletHoldRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletHoldRepository.Claim;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletRepository.WalletSnapshot;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.TxnSummary;
import cn.tolim.pudans.infra.r2dbc.ai.wallet.AiWalletTxnRepository.WalletTxn;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiWalletServiceTest {

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
        service.setClock(Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void ensuresAWalletForANewUser() {
        when(walletRepository.ensure("user-1")).thenReturn(Mono.empty());

        StepVerifier.create(service.ensureWallet("user-1")).verifyComplete();

        verify(walletRepository).ensure("user-1");
    }

    @Test
    void freezesFundsAndReportsBalances() {
        var amount = new BigDecimal("10.00");
        var expiresAt = Instant.parse("2026-01-01T00:05:00Z");
        when(holdRepository.insertIfAbsent("req-freeze", "user-1", amount, expiresAt))
                .thenReturn(Mono.just(true));
        when(walletRepository.reserve("user-1", amount))
                .thenReturn(Mono.just(new WalletSnapshot(new BigDecimal("100.00"), new BigDecimal("10.00"))));

        StepVerifier.create(service.freeze("req-freeze", "user-1", amount, Duration.ofMinutes(5)))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isFalse();
                    assertThat(result.amount()).isEqualByComparingTo("10.00");
                    assertThat(result.balance()).isEqualByComparingTo("100.00");
                    assertThat(result.frozen()).isEqualByComparingTo("10.00");
                    assertThat(result.available()).isEqualByComparingTo("90.00");
                })
                .verifyComplete();
    }

    @Test
    void rejectsFreezeWhenAvailableBalanceIsInsufficient() {
        var amount = new BigDecimal("10.00");
        when(holdRepository.insertIfAbsent(
                        "req-insufficient", "user-1", amount, Instant.parse("2026-01-01T00:05:00Z")))
                .thenReturn(Mono.just(true));
        when(walletRepository.reserve("user-1", amount)).thenReturn(Mono.empty());
        when(holdRepository.deleteIfHeld("req-insufficient")).thenReturn(Mono.just(true));

        StepVerifier.create(service.freeze("req-insufficient", "user-1", amount, Duration.ofMinutes(5)))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode()).isEqualTo("ai-wallet-insufficient");
                })
                .verify();

        verify(holdRepository).deleteIfHeld("req-insufficient");
    }

    @Test
    void returnsTheExistingHoldWhenFreezeRepeats() {
        var amount = new BigDecimal("10.00");
        when(holdRepository.insertIfAbsent(
                        "req-freeze-repeat", "user-1", amount, Instant.parse("2026-01-01T00:05:00Z")))
                .thenReturn(Mono.just(false));
        when(holdRepository.findClaim("req-freeze-repeat")).thenReturn(Mono.just(new Claim("user-1", amount)));
        when(walletRepository.findByUserId("user-1"))
                .thenReturn(Mono.just(new WalletSnapshot(new BigDecimal("100.00"), new BigDecimal("10.00"))));

        StepVerifier.create(service.freeze("req-freeze-repeat", "user-1", amount, Duration.ofMinutes(5)))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isTrue();
                    assertThat(result.amount()).isEqualByComparingTo("10.00");
                    assertThat(result.available()).isEqualByComparingTo("90.00");
                })
                .verifyComplete();

        verifyNoInteractions(txnRepository);
    }

    @Test
    void settlesTheActualCostWhenItFitsTheHold() {
        var holdAmount = new BigDecimal("10.00");
        var chargedCost = new BigDecimal("3.00");
        when(txnRepository.findByIdempotencyKey("settle:req-normal")).thenReturn(Mono.empty());
        when(holdRepository.claimSettled("req-normal", "user-1"))
                .thenReturn(Mono.just(new Claim("user-1", holdAmount)));
        when(walletRepository.deductForSettle("user-1", holdAmount, chargedCost))
                .thenReturn(Mono.just(new WalletSnapshot(new BigDecimal("97.00"), new BigDecimal("0.00"))));
        when(txnRepository.insertIfAbsent(any(WalletTxn.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.settle(
                        "req-normal", "user-1", holdAmount, chargedCost, AiWalletUsage.EMPTY))
                .assertNext(result -> {
                    assertThat(result.chargedCost()).isEqualByComparingTo("3.00");
                    assertThat(result.balance()).isEqualByComparingTo("97.00");
                    assertThat(result.duplicate()).isFalse();
                })
                .verifyComplete();
    }

    @Test
    void settlesAtTheHoldAmountWhenActualCostExceedsIt() {
        var holdAmount = new BigDecimal("10.00");
        when(txnRepository.findByIdempotencyKey("settle:req-capped")).thenReturn(Mono.empty());
        when(holdRepository.claimSettled("req-capped", "user-1"))
                .thenReturn(Mono.just(new Claim("user-1", holdAmount)));
        when(walletRepository.deductForSettle("user-1", holdAmount, holdAmount))
                .thenReturn(Mono.just(new WalletSnapshot(new BigDecimal("90.00"), new BigDecimal("0.00"))));
        when(txnRepository.insertIfAbsent(any(WalletTxn.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.settle(
                        "req-capped", "user-1", holdAmount, new BigDecimal("25.00"), AiWalletUsage.EMPTY))
                .assertNext(result -> {
                    assertThat(result.chargedCost()).isEqualByComparingTo("10.00");
                    assertThat(result.balance()).isEqualByComparingTo("90.00");
                    assertThat(result.duplicate()).isFalse();
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(WalletTxn.class);
        verify(txnRepository).insertIfAbsent(captor.capture());
        var txn = captor.getValue();
        assertThat(txn.txnType()).isEqualTo("CHARGE");
        assertThat(txn.amount()).isEqualByComparingTo("-10.00");
        assertThat(txn.balanceAfter()).isEqualByComparingTo("90.00");
        assertThat(txn.idempotencyKey()).isEqualTo("settle:req-capped");
        assertThat(txn.requestId()).isEqualTo("req-capped");
    }

    @Test
    void returnsTheExistingTxnWhenSettleRepeats() {
        when(txnRepository.findByIdempotencyKey("settle:req-repeat"))
                .thenReturn(Mono.just(new TxnSummary(
                        "user-1", new BigDecimal("-4.00"), new BigDecimal("96.00"), "settle:req-repeat")));

        StepVerifier.create(service.settle(
                        "req-repeat", "user-1", new BigDecimal("5.00"), new BigDecimal("4.00"), AiWalletUsage.EMPTY))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isTrue();
                    assertThat(result.chargedCost()).isEqualByComparingTo("4.00");
                    assertThat(result.balance()).isEqualByComparingTo("96.00");
                    assertThat(result.idempotencyKey()).isEqualTo("settle:req-repeat");
                })
                .verifyComplete();

        verifyNoInteractions(walletRepository, holdRepository);
        verify(txnRepository, never()).insertIfAbsent(any(WalletTxn.class));
    }

    @Test
    void releasesTheHoldAndWritesAnUnfreezeTxn() {
        var amount = new BigDecimal("10.00");
        when(holdRepository.claimReleased("req-release")).thenReturn(Mono.just(new Claim("user-1", amount)));
        when(walletRepository.releaseFrozen("user-1", amount))
                .thenReturn(Mono.just(new WalletSnapshot(new BigDecimal("100.00"), new BigDecimal("0.00"))));
        when(txnRepository.insertIfAbsent(any(WalletTxn.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.release("req-release"))
                .assertNext(result -> {
                    assertThat(result.released()).isTrue();
                    assertThat(result.idempotencyKey()).isEqualTo("release:req-release");
                })
                .verifyComplete();

        var captor = ArgumentCaptor.forClass(WalletTxn.class);
        verify(txnRepository).insertIfAbsent(captor.capture());
        var txn = captor.getValue();
        assertThat(txn.txnType()).isEqualTo("UNFREEZE");
        assertThat(txn.amount()).isEqualByComparingTo("0.00");
        assertThat(txn.balanceAfter()).isEqualByComparingTo("100.00");
        assertThat(txn.requestId()).isEqualTo("req-release");
        assertThat(txn.idempotencyKey()).isEqualTo("release:req-release");
    }

    @Test
    void releaseIsIdempotentWhenTheHoldIsNotHeld() {
        when(holdRepository.claimReleased("req-released")).thenReturn(Mono.empty());

        StepVerifier.create(service.release("req-released"))
                .assertNext(result -> {
                    assertThat(result.released()).isFalse();
                    assertThat(result.idempotencyKey()).isEqualTo("release:req-released");
                })
                .verifyComplete();

        verifyNoInteractions(walletRepository);
        verify(txnRepository, never()).insertIfAbsent(any(WalletTxn.class));
    }

    @Test
    void sweepsExpiredHoldsAndReleasesEachOne() {
        when(holdRepository.findExpiredRequestIds(AiWalletService.SWEEP_BATCH_SIZE))
                .thenReturn(Flux.just("req-expired-a", "req-expired-b"));
        when(holdRepository.claimReleased("req-expired-a"))
                .thenReturn(Mono.just(new Claim("user-1", new BigDecimal("5.00"))));
        when(holdRepository.claimReleased("req-expired-b"))
                .thenReturn(Mono.just(new Claim("user-2", new BigDecimal("7.00"))));
        when(walletRepository.releaseFrozen("user-1", new BigDecimal("5.00")))
                .thenReturn(Mono.just(new WalletSnapshot(new BigDecimal("50.00"), new BigDecimal("0.00"))));
        when(walletRepository.releaseFrozen("user-2", new BigDecimal("7.00")))
                .thenReturn(Mono.just(new WalletSnapshot(new BigDecimal("70.00"), new BigDecimal("0.00"))));
        when(txnRepository.insertIfAbsent(any(WalletTxn.class))).thenReturn(Mono.just(true));

        StepVerifier.create(service.sweepExpired())
                .assertNext(result -> assertThat(result.releasedCount()).isEqualTo(2))
                .verifyComplete();

        verify(holdRepository).claimReleased("req-expired-a");
        verify(holdRepository).claimReleased("req-expired-b");
    }
}
