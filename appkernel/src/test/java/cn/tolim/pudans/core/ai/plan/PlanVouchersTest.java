package cn.tolim.pudans.core.ai.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiPlan;
import cn.tolim.pudans.contract.rbac.resource.UserGroup;
import cn.tolim.pudans.contract.user.resource.User;
import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.core.rbac.group.Groups;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository.PlanCounterRow;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanVoucherRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanVoucherRepository.VoucherCounterRow;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanVoucherRepository.VoucherUseRow;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
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
class PlanVouchersTest {

    private static final String UID = "uid-alice";

    private static final String USERNAME = "alice";

    private static final String REQUEST_ID = "use-1";

    private static final String PERIOD_KEY = "2026-11";

    private static final String FIVE_HOUR = PlanVoucherKind.FIVE_HOUR_RESET.name();

    private static final String WEEKLY = PlanVoucherKind.WEEKLY_RESET.name();

    @Mock
    PlanResolution resolutionService;

    @Mock
    PlanCounterRepository counterRepository;

    @Mock
    PlanVoucherRepository voucherRepository;

    @Mock
    Accounts accounts;

    @Mock
    Groups groups;

    @Mock
    TransactionalOperator transactionalOperator;

    private PlanWindowClock windowClock;

    private PlanVouchers service;

    private AiPlan plan;

    private PlanWindow fiveWindow;

    private PlanWindow weekWindow;

    @BeforeEach
    void setUp() {
        windowClock = new PlanWindowClock();
        windowClock.setClock(Clock.fixed(PlanTestFixtures.NOW, ZoneOffset.UTC));
        service = new PlanVouchers(
                resolutionService,
                counterRepository,
                voucherRepository,
                windowClock,
                accounts,
                groups,
                transactionalOperator);
        plan = PlanTestFixtures.plan("standard");
        plan.getSpec().getVouchers().setFiveHour(2);
        plan.getSpec().getVouchers().setWeekly(1);
        fiveWindow = PlanTestFixtures.activeFiveHour(0, 0);
        weekWindow = PlanTestFixtures.activeWeek();
        lenient()
                .when(resolutionService.resolve(UID))
                .thenReturn(Mono.just(new PlanResolution.ResolvedPlan(
                        "standard", "套餐 standard", "User/" + UID, plan)));
        lenient()
                .when(transactionalOperator.transactional(ArgumentMatchers.<Mono<Object>>any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient()
                .when(voucherRepository.syncEntitlement(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(Mono.empty());
        lenient().when(voucherRepository.findUse(anyString())).thenReturn(Mono.empty());
        lenient()
                .when(voucherRepository.findCounter(anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
    }

    @Test
    void fallsBackToPlanEntitlementsWithoutCounterRows() {
        StepVerifier.create(service.totals(UID, plan))
                .assertNext(balance -> {
                    assertThat(balance.fiveHour().total()).isEqualTo(2);
                    assertThat(balance.fiveHour().used()).isZero();
                    assertThat(balance.fiveHour().remaining()).isEqualTo(2);
                    assertThat(balance.weekly().total()).isEqualTo(1);
                    assertThat(balance.weekly().remaining()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void reportsGrantedBonusesAndUsage() {
        when(voucherRepository.findCounter(UID, FIVE_HOUR, PERIOD_KEY))
                .thenReturn(Mono.just(new VoucherCounterRow(UID, FIVE_HOUR, PERIOD_KEY, 2, 1, 1)));
        when(voucherRepository.findCounter(UID, WEEKLY, PERIOD_KEY))
                .thenReturn(Mono.just(new VoucherCounterRow(UID, WEEKLY, PERIOD_KEY, 1, 0, 1)));

        StepVerifier.create(service.balance(UID))
                .assertNext(balance -> {
                    assertThat(balance.fiveHour().total()).isEqualTo(3);
                    assertThat(balance.fiveHour().used()).isEqualTo(1);
                    assertThat(balance.fiveHour().remaining()).isEqualTo(2);
                    assertThat(balance.weekly().remaining()).isZero();
                })
                .verifyComplete();
    }

    @Test
    void consumesAFiveHourVoucherAndResetsTheWindow() {
        stubLockedCounters(2, 0, 0, 1000, 0, 500, 0);
        when(voucherRepository.consume(UID, FIVE_HOUR, PERIOD_KEY))
                .thenReturn(Mono.just(new VoucherCounterRow(UID, FIVE_HOUR, PERIOD_KEY, 2, 0, 1)));
        when(counterRepository.resetWindow(
                        UID,
                        "FIVE_HOUR",
                        PlanWindowClock.fiveHourKey(PlanTestFixtures.NOW),
                        PlanTestFixtures.NOW,
                        PlanTestFixtures.NOW.plusSeconds(5 * 3600),
                        3_000_000L,
                        PlanWindowClock.ANCHOR_PLAN))
                .thenReturn(Mono.empty());
        stubUseInsert(true);

        StepVerifier.create(service.use(REQUEST_ID, UID, PlanVoucherKind.FIVE_HOUR_RESET))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isFalse();
                    assertThat(result.targetWindowKey()).isEqualTo(fiveWindow.windowKey());
                    assertThat(result.windowKeyAfter())
                            .isEqualTo(PlanWindowClock.fiveHourKey(PlanTestFixtures.NOW));
                })
                .verifyComplete();

        verify(counterRepository)
                .resetWindow(
                        UID,
                        "FIVE_HOUR",
                        PlanWindowClock.fiveHourKey(PlanTestFixtures.NOW),
                        PlanTestFixtures.NOW,
                        PlanTestFixtures.NOW.plusSeconds(5 * 3600),
                        3_000_000L,
                        PlanWindowClock.ANCHOR_PLAN);
    }

    @Test
    void consumesAWeeklyVoucherAndAnchorsTheNewWindow() {
        stubLockedCounters(1, 2, 0, 1000, 0, 500, 0);
        when(voucherRepository.consume(UID, WEEKLY, PERIOD_KEY))
                .thenReturn(Mono.just(new VoucherCounterRow(UID, WEEKLY, PERIOD_KEY, 1, 0, 1)));
        when(counterRepository.resetWindow(
                        UID,
                        "WEEKLY",
                        PlanWindowClock.weekKey(PlanTestFixtures.NOW),
                        PlanTestFixtures.NOW,
                        PlanTestFixtures.NOW.plusSeconds(7 * 24 * 3600),
                        20_000_000L,
                        PlanWindowClock.ANCHOR_VOUCHER))
                .thenReturn(Mono.empty());
        stubUseInsert(true);

        StepVerifier.create(service.use(REQUEST_ID, UID, PlanVoucherKind.WEEKLY_RESET))
                .assertNext(result -> {
                    assertThat(result.targetWindowKey()).isEqualTo(weekWindow.windowKey());
                    assertThat(result.windowKeyAfter())
                            .isEqualTo(PlanWindowClock.weekKey(PlanTestFixtures.NOW));
                })
                .verifyComplete();

        verify(counterRepository)
                .resetWindow(
                        UID,
                        "WEEKLY",
                        PlanWindowClock.weekKey(PlanTestFixtures.NOW),
                        PlanTestFixtures.NOW,
                        PlanTestFixtures.NOW.plusSeconds(7 * 24 * 3600),
                        20_000_000L,
                        PlanWindowClock.ANCHOR_VOUCHER);
    }

    @Test
    void rejectsAUseWithoutAvailableVouchers() {
        stubLockedCounters(0, 0, 0, 1000, 0, 500, 0);

        StepVerifier.create(service.use(REQUEST_ID, UID, PlanVoucherKind.FIVE_HOUR_RESET))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanVouchers.CODE_VOUCHER_MISSING))
                .verify();

        verify(voucherRepository, never()).consume(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsAResetWhenTheTargetWindowIsEmpty() {
        stubLockedCounters(2, 0, 0, 0, 0, 500, 0);

        StepVerifier.create(service.use(REQUEST_ID, UID, PlanVoucherKind.FIVE_HOUR_RESET))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanVouchers.CODE_RESET_NOT_NEEDED))
                .verify();
    }

    @Test
    void rejectsAResetWhileRequestsAreInFlight() {
        stubLockedCounters(2, 0, 0, 1000, 100, 500, 0);

        StepVerifier.create(service.use(REQUEST_ID, UID, PlanVoucherKind.FIVE_HOUR_RESET))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanVouchers.CODE_RESET_BUSY))
                .verify();
    }

    @Test
    void replaysADuplicateVoucherUse() {
        stubLockedCounters(2, 0, 0, 1000, 0, 500, 0);
        when(voucherRepository.findUse(REQUEST_ID))
                .thenReturn(Mono.just(new VoucherUseRow(
                        1L,
                        REQUEST_ID,
                        UID,
                        FIVE_HOUR,
                        PERIOD_KEY,
                        fiveWindow.windowKey(),
                        "5h:2026-11-18T10:00:00Z",
                        PlanTestFixtures.NOW)));

        StepVerifier.create(service.use(REQUEST_ID, UID, PlanVoucherKind.FIVE_HOUR_RESET))
                .assertNext(result -> {
                    assertThat(result.duplicate()).isTrue();
                    assertThat(result.windowKeyAfter()).isEqualTo("5h:2026-11-18T10:00:00Z");
                })
                .verifyComplete();

        verify(voucherRepository, never()).consume(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsReusingARequestForAnotherVoucherKind() {
        stubLockedCounters(1, 0, 0, 1000, 0, 500, 0);
        when(voucherRepository.findUse(REQUEST_ID))
                .thenReturn(Mono.just(new VoucherUseRow(
                        1L,
                        REQUEST_ID,
                        UID,
                        WEEKLY,
                        PERIOD_KEY,
                        weekWindow.windowKey(),
                        weekWindow.windowKey(),
                        PlanTestFixtures.NOW)));

        StepVerifier.create(service.use(REQUEST_ID, UID, PlanVoucherKind.FIVE_HOUR_RESET))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanVouchers.CODE_VOUCHER_CONFLICT))
                .verify();
    }

    @Test
    void rejectsAConcurrentVoucherUse() {
        stubLockedCounters(2, 0, 0, 1000, 0, 500, 0);
        when(voucherRepository.consume(UID, FIVE_HOUR, PERIOD_KEY))
                .thenReturn(Mono.just(new VoucherCounterRow(UID, FIVE_HOUR, PERIOD_KEY, 2, 0, 1)));
        when(counterRepository.resetWindow(anyString(), anyString(), anyString(), any(), any(), anyLong(), anyString()))
                .thenReturn(Mono.empty());
        stubUseInsert(false);

        StepVerifier.create(service.use(REQUEST_ID, UID, PlanVoucherKind.FIVE_HOUR_RESET))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanVouchers.CODE_VOUCHER_CONFLICT))
                .verify();
    }

    @Test
    void grantsVouchersToAUser() {
        when(voucherRepository.addBonus(UID, FIVE_HOUR, PERIOD_KEY, 2, 3))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.grant(
                        PlanVoucherKind.FIVE_HOUR_RESET, 3, List.of(UID), null))
                .assertNext(result -> {
                    assertThat(result.count()).isEqualTo(3);
                    assertThat(result.userIds()).containsExactly(UID);
                })
                .verifyComplete();

        verify(voucherRepository).addBonus(UID, FIVE_HOUR, PERIOD_KEY, 2, 3);
    }

    @Test
    void grantsVouchersToUserGroupMembers() {
        var user = new User();
        user.getSpec().setUid(UID);
        when(groups.getUserGroup("rd")).thenReturn(Mono.just(new UserGroup()));
        when(groups.listUsernamesOfGroups(List.of("rd"))).thenReturn(Flux.just(USERNAME));
        when(accounts.getUser(USERNAME)).thenReturn(Mono.just(user));
        when(voucherRepository.addBonus(UID, FIVE_HOUR, PERIOD_KEY, 2, 1))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.grant(
                        PlanVoucherKind.FIVE_HOUR_RESET, 1, null, List.of("rd")))
                .assertNext(result -> assertThat(result.userIds()).containsExactly(UID))
                .verifyComplete();
    }

    @Test
    void rejectsGrantingToAnUnknownUserGroup() {
        when(groups.getUserGroup("missing")).thenReturn(Mono.empty());

        StepVerifier.create(service.grant(
                        PlanVoucherKind.FIVE_HOUR_RESET, 1, null, List.of("missing")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanVouchers.CODE_GRANT_INVALID))
                .verify();
    }

    @Test
    void rejectsAGrantWithoutTargetsOrAPositiveCount() {
        StepVerifier.create(service.grant(PlanVoucherKind.FIVE_HOUR_RESET, 0, List.of(UID), null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanVouchers.CODE_GRANT_INVALID))
                .verify();

        StepVerifier.create(service.grant(PlanVoucherKind.FIVE_HOUR_RESET, 1, null, null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(ApiException.class)
                        .hasFieldOrPropertyWithValue(
                                "code", PlanVouchers.CODE_GRANT_INVALID))
                .verify();
    }

    @Test
    void listsVoucherUsageHistory() {
        when(voucherRepository.listUses(UID, 9L, 20))
                .thenReturn(Flux.just(new VoucherUseRow(
                        7L,
                        REQUEST_ID,
                        UID,
                        FIVE_HOUR,
                        PERIOD_KEY,
                        fiveWindow.windowKey(),
                        "5h:2026-11-18T10:00:00Z",
                        PlanTestFixtures.NOW)));

        StepVerifier.create(service.history(UID, 9L, 20))
                .assertNext(record -> {
                    assertThat(record.id()).isEqualTo(7L);
                    assertThat(record.requestId()).isEqualTo(REQUEST_ID);
                    assertThat(record.kind()).isEqualTo(FIVE_HOUR);
                })
                .verifyComplete();
    }

    @Test
    void derivesVoucherPeriodKeysInBeijing() {
        assertThat(PlanVouchers.periodKey(AiPlan.VoucherPeriod.MONTHLY, PlanTestFixtures.NOW))
                .isEqualTo(PERIOD_KEY);
        assertThat(PlanVouchers.periodKey(AiPlan.VoucherPeriod.WEEKLY, PlanTestFixtures.NOW))
                .isEqualTo("2026-W47");
    }

    private void stubLockedCounters(
            int voucherEntitlement,
            int voucherBonus,
            int voucherUsed,
            long fiveUsed,
            long fiveReserved,
            long weeklyUsed,
            long weeklyReserved) {
        when(voucherRepository.lockCounter(anyString(), anyString(), anyString()))
                .thenReturn(Mono.just(new VoucherCounterRow(
                        UID, FIVE_HOUR, PERIOD_KEY, voucherEntitlement, voucherBonus, voucherUsed)));
        when(counterRepository.ensureRows(UID)).thenReturn(Mono.empty());
        when(counterRepository.lockCounter(UID, "FIVE_HOUR"))
                .thenReturn(Mono.just(row(
                        PlanWindowKind.FIVE_HOUR,
                        fiveWindow,
                        3_000_000L,
                        fiveUsed,
                        fiveReserved)));
        when(counterRepository.lockCounter(UID, "WEEKLY"))
                .thenReturn(Mono.just(row(
                        PlanWindowKind.WEEKLY, weekWindow, 20_000_000L, weeklyUsed, weeklyReserved)));
    }

    private void stubUseInsert(boolean inserted) {
        when(voucherRepository.insertUseIfAbsent(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any()))
                .thenReturn(Mono.just(inserted));
    }

    private static PlanCounterRow row(
            PlanWindowKind kind, PlanWindow window, long limit, long used, long reserved) {
        return PlanTestFixtures.counter(
                kind, window.windowKey(), window.startsAt(), window.endsAt(), limit, used, reserved);
    }
}