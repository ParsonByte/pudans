package cn.tolim.pudans.core.ai.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiPlan;
import cn.tolim.pudans.contract.ai.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository.PlanCounterRow;
import cn.tolim.pudans.infra.r2dbc.ai.plan.UsageLedgerRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.UsageLedgerRepository.LedgerClaim;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PlanQuotasTest {

    private static final String UID = "uid-alice";

    private static final String REQUEST_ID = "req-1";

    private static final long FIVE_HOUR_LIMIT = 3_000_000L;

    private static final long WEEKLY_LIMIT = 20_000_000L;

    @Mock
    PlanCounterRepository counterRepository;

    @Mock
    UsageLedgerRepository ledgerRepository;

    @Mock
    AiSettings aiSettings;

    @Mock
    TransactionalOperator transactionalOperator;

    private PlanWindowClock windowClock;

    private PlanQuotas service;

    private AiPlan plan;

    private PlanWindow fiveWindow;

    private PlanWindow weekWindow;

    @BeforeEach
    void setUp() {
        windowClock = new PlanWindowClock();
        windowClock.setClock(Clock.fixed(PlanTestFixtures.NOW, ZoneOffset.UTC));
        service = new PlanQuotas(
                counterRepository, ledgerRepository, aiSettings, windowClock, transactionalOperator);
        plan = PlanTestFixtures.plan("standard");
        fiveWindow = PlanTestFixtures.activeFiveHour(0, 0);
        weekWindow = PlanTestFixtures.activeWeek();
        lenient()
                .when(transactionalOperator.transactional(ArgumentMatchers.<Mono<Object>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(aiSettings.spec())
                .thenReturn(Mono.just(PlanTestFixtures.setting(
                        "standard", AiSetting.UnassignedPolicy.DEFAULT_PLAN)));
    }

    @Test
    void reservesUnitsInBothWindowsAndWritesTheLedger() {
        stubActiveCounters(0, 0);
        when(counterRepository.reserve(UID, "FIVE_HOUR", fiveWindow.windowKey(), 1000))
                .thenReturn(Mono.empty());
        when(counterRepository.reserve(UID, "WEEKLY", weekWindow.windowKey(), 1000))
                .thenReturn(Mono.empty());
        stubLedgerInsert(true);

        StepVerifier.create(reserve(1000))
                .assertNext(result -> {
                    assertThat(result.planId()).isEqualTo("standard");
                    assertThat(result.units()).isEqualTo(1000);
                    assertThat(result.fiveHourWindowKey()).isEqualTo(fiveWindow.windowKey());
                    assertThat(result.weeklyWindowKey()).isEqualTo(weekWindow.windowKey());
                    assertThat(result.duplicate()).isFalse();
                })
                .verifyComplete();

        verify(ledgerRepository)
                .insertReserved(
                        REQUEST_ID,
                        UID,
                        null,
                        "standard",
                        "USER",
                        "CHAT",
                        fiveWindow.windowKey(),
                        weekWindow.windowKey(),
                        1000L,
                        "trace-1");
    }

    @Test
    void rejectsWhenTheFiveHourWindowIsExhausted() {
        stubActiveCounters(FIVE_HOUR_LIMIT - 500, 0);

        StepVerifier.create(reserve(1000))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanQuotas.CODE_QUOTA_EXCEEDED))
                .verify();

        verify(counterRepository, never()).reserve(anyString(), anyString(), anyString(), anyLong());
        verify(ledgerRepository, never())
                .insertReserved(
                        anyString(), anyString(), any(), any(), anyString(), anyString(),
                        any(), any(), anyLong(), any());
    }

    @Test
    void rejectsWhenTheWeeklyWindowIsExhausted() {
        stubActiveCounters(0, WEEKLY_LIMIT - 500);

        StepVerifier.create(reserve(1000))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanQuotas.CODE_QUOTA_EXCEEDED))
                .verify();

        verify(counterRepository, never()).reserve(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    void resetsExpiredWindowsAndSyncsStaleLimits() {
        var expired = PlanTestFixtures.counter(
                PlanWindowKind.FIVE_HOUR,
                "5h:old",
                PlanTestFixtures.NOW.minusSeconds(6 * 3600),
                PlanTestFixtures.NOW.minusSeconds(3600),
                1_000_000L,
                0,
                0);
        var staleWeekly = PlanTestFixtures.counter(
                PlanWindowKind.WEEKLY,
                weekWindow.windowKey(),
                weekWindow.startsAt(),
                weekWindow.endsAt(),
                10_000_000L,
                0,
                0);
        when(counterRepository.ensureRows(UID)).thenReturn(Mono.empty());
        when(counterRepository.lockCounter(UID, "FIVE_HOUR")).thenReturn(Mono.just(expired));
        when(counterRepository.lockCounter(UID, "WEEKLY")).thenReturn(Mono.just(staleWeekly));
        when(counterRepository.resetWindow(
                        UID,
                        "FIVE_HOUR",
                        PlanWindowClock.fiveHourKey(PlanTestFixtures.NOW),
                        PlanTestFixtures.NOW,
                        PlanTestFixtures.NOW.plusSeconds(5 * 3600),
                        FIVE_HOUR_LIMIT,
                        PlanWindowClock.ANCHOR_PLAN))
                .thenReturn(Mono.empty());
        when(counterRepository.syncQuotaLimit(UID, "WEEKLY", WEEKLY_LIMIT))
                .thenReturn(Mono.empty());
        when(counterRepository.reserve(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.empty());
        stubLedgerInsert(true);

        StepVerifier.create(reserve(1000))
                .assertNext(result -> assertThat(result.fiveHourWindowKey())
                        .isEqualTo(PlanWindowClock.fiveHourKey(PlanTestFixtures.NOW)))
                .verifyComplete();

        verify(counterRepository)
                .resetWindow(
                        UID,
                        "FIVE_HOUR",
                        PlanWindowClock.fiveHourKey(PlanTestFixtures.NOW),
                        PlanTestFixtures.NOW,
                        PlanTestFixtures.NOW.plusSeconds(5 * 3600),
                        FIVE_HOUR_LIMIT,
                        PlanWindowClock.ANCHOR_PLAN);
        verify(counterRepository).syncQuotaLimit(UID, "WEEKLY", WEEKLY_LIMIT);
    }

    @Test
    void replaysADuplicateReservation() {
        stubActiveCounters(0, 0);
        when(counterRepository.reserve(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.empty());
        stubLedgerInsert(false);
        when(ledgerRepository.findByRequestId(REQUEST_ID))
                .thenReturn(Mono.just(PlanTestFixtures.ledgerRow(
                        REQUEST_ID, UID, "RESERVED", 1000)));

        StepVerifier.create(reserve(1000))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isTrue();
                    assertThat(result.units()).isEqualTo(1000);
                })
                .verifyComplete();
    }

    @Test
    void rejectsADuplicateReservationForAnotherUser() {
        stubActiveCounters(0, 0);
        when(counterRepository.reserve(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.empty());
        stubLedgerInsert(false);
        when(ledgerRepository.findByRequestId(REQUEST_ID))
                .thenReturn(Mono.just(PlanTestFixtures.ledgerRow(
                        REQUEST_ID, "uid-other", "RESERVED", 1000)));

        StepVerifier.create(reserve(1000))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanQuotas.CODE_RESERVE_CONFLICT))
                .verify();
    }

    @Test
    void rejectsAnInvalidPlanBeforeOpeningATransaction() {
        plan.getSpec().setEnabled(false);

        StepVerifier.create(reserve(1000))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue("code", PlanCatalog.CODE_INVALID))
                .verify();

        verify(transactionalOperator, never()).transactional(ArgumentMatchers.<Mono<Object>>any());
    }

    @Test
    void settlesAgainstTheReservedWindowKeys() {
        var claim = new LedgerClaim(UID, "standard", 900L, fiveWindow.windowKey(), weekWindow.windowKey());
        when(ledgerRepository.claimSettled(eq(REQUEST_ID), eq(1000L), any(PlanUsageSnapshot.class)))
                .thenReturn(Mono.just(claim));
        when(counterRepository.settle(
                        UID, "FIVE_HOUR", fiveWindow.windowKey(), 900L, 1000L))
                .thenReturn(Mono.empty());
        when(counterRepository.settle(
                        UID, "WEEKLY", weekWindow.windowKey(), 900L, 1000L))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.settle(REQUEST_ID, 1000, PlanUsageSnapshot.EMPTY))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isFalse();
                    assertThat(result.units()).isEqualTo(1000);
                })
                .verifyComplete();

        verify(counterRepository)
                .settle(UID, "FIVE_HOUR", fiveWindow.windowKey(), 900L, 1000L);
        verify(counterRepository)
                .settle(UID, "WEEKLY", weekWindow.windowKey(), 900L, 1000L);
    }

    @Test
    void replaysADuplicateSettlement() {
        when(ledgerRepository.claimSettled(anyString(), anyLong(), any(PlanUsageSnapshot.class)))
                .thenReturn(Mono.empty());
        when(ledgerRepository.findByRequestId(REQUEST_ID))
                .thenReturn(Mono.just(PlanTestFixtures.ledgerRow(
                        REQUEST_ID, UID, "SETTLED", 800)));

        StepVerifier.create(service.settle(REQUEST_ID, 1000, null))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isTrue();
                    assertThat(result.units()).isEqualTo(800);
                })
                .verifyComplete();
    }

    @Test
    void rejectsSettlingAReleasedReservation() {
        when(ledgerRepository.claimSettled(anyString(), anyLong(), any(PlanUsageSnapshot.class)))
                .thenReturn(Mono.empty());
        when(ledgerRepository.findByRequestId(REQUEST_ID))
                .thenReturn(Mono.just(PlanTestFixtures.ledgerRow(
                        REQUEST_ID, UID, "RELEASED", 800)));

        StepVerifier.create(service.settle(REQUEST_ID, 1000, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanQuotas.CODE_SETTLE_CONFLICT))
                .verify();
    }

    @Test
    void releasesReservedUnitsFromBothWindows() {
        var claim = new LedgerClaim(UID, "standard", 900L, fiveWindow.windowKey(), weekWindow.windowKey());
        when(ledgerRepository.claimReleased(REQUEST_ID)).thenReturn(Mono.just(claim));
        when(counterRepository.release(
                        UID, "FIVE_HOUR", fiveWindow.windowKey(), 900L))
                .thenReturn(Mono.empty());
        when(counterRepository.release(
                        UID, "WEEKLY", weekWindow.windowKey(), 900L))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.release(REQUEST_ID))
                .assertNext(result -> assertThat(result.released()).isTrue())
                .verifyComplete();
    }

    @Test
    void treatsReleasingANonReservedRequestAsIdempotent() {
        when(ledgerRepository.claimReleased(REQUEST_ID)).thenReturn(Mono.empty());

        StepVerifier.create(service.release(REQUEST_ID))
                .assertNext(result -> assertThat(result.released()).isFalse())
                .verifyComplete();
    }

    @Test
    void sweepsExpiredReservationsOnly() {
        when(ledgerRepository.findStaleReserved(PlanTestFixtures.NOW.minusSeconds(600), 100))
                .thenReturn(Flux.just("req-stale", "req-gone"));
        when(ledgerRepository.claimReleased("req-stale"))
                .thenReturn(Mono.just(new LedgerClaim(
                        UID, "standard", 10L, fiveWindow.windowKey(), weekWindow.windowKey())));
        when(ledgerRepository.claimReleased("req-gone")).thenReturn(Mono.empty());
        when(counterRepository.release(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.sweepExpired())
                .assertNext(result -> assertThat(result.releasedCount()).isEqualTo(1))
                .verifyComplete();
    }

    private void stubActiveCounters(long fiveUsed, long weeklyUsed) {
        var five = PlanTestFixtures.counter(
                PlanWindowKind.FIVE_HOUR,
                fiveWindow.windowKey(),
                fiveWindow.startsAt(),
                fiveWindow.endsAt(),
                FIVE_HOUR_LIMIT,
                fiveUsed,
                0);
        var weekly = PlanTestFixtures.counter(
                PlanWindowKind.WEEKLY,
                weekWindow.windowKey(),
                weekWindow.startsAt(),
                weekWindow.endsAt(),
                WEEKLY_LIMIT,
                weeklyUsed,
                0);
        when(counterRepository.ensureRows(UID)).thenReturn(Mono.empty());
        when(counterRepository.lockCounter(UID, "FIVE_HOUR")).thenReturn(Mono.just(five));
        when(counterRepository.lockCounter(UID, "WEEKLY")).thenReturn(Mono.just(weekly));
    }

    private void stubLedgerInsert(boolean inserted) {
        when(ledgerRepository.insertReserved(
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        anyString(),
                        anyString(),
                        any(),
                        any(),
                        anyLong(),
                        any()))
                .thenReturn(Mono.just(inserted));
    }

    private Mono<PlanQuotas.ReserveResult> reserve(long units) {
        return service.reserve(REQUEST_ID, UID, plan, units, "CHAT", null, "trace-1");
    }
}