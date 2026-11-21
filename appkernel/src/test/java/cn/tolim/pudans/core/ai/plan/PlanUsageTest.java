package cn.tolim.pudans.core.ai.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiPlan;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository;
import cn.tolim.pudans.infra.r2dbc.ai.plan.PlanCounterRepository.PlanCounterRow;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class PlanUsageTest {

    private static final String UID = "uid-alice";

    private static final long FIVE_HOUR_LIMIT = 3_000_000L;

    private static final long WEEKLY_LIMIT = 20_000_000L;

    @Mock
    PlanResolution planResolution;

    @Mock
    PlanCounterRepository counterRepository;

    @Mock
    PlanVouchers planVouchers;

    private PlanWindowClock windowClock;

    private PlanUsage service;

    private AiPlan plan;

    private PlanWindow fiveWindow;

    private PlanWindow weekWindow;

    @BeforeEach
    void setUp() {
        windowClock = new PlanWindowClock();
        windowClock.setClock(Clock.fixed(PlanTestFixtures.NOW, ZoneOffset.UTC));
        service = new PlanUsage(
                planResolution, counterRepository, planVouchers, windowClock);
        plan = PlanTestFixtures.plan("standard");
        fiveWindow = PlanTestFixtures.activeFiveHour(0, 0);
        weekWindow = PlanTestFixtures.activeWeek();
        var resolved = new PlanResolution.ResolvedPlan(
                "standard", "套餐 standard", "User/" + UID, plan);
        when(planResolution.resolve(UID)).thenReturn(Mono.just(resolved));
        when(planVouchers.totals(UID, plan))
                .thenReturn(Mono.just(new PlanVouchers.VoucherBalance(
                        new PlanVouchers.Balance(2, 0, 2),
                        new PlanVouchers.Balance(1, 0, 1))));
    }

    @Test
    void projectsActiveWindowUsage() {
        when(counterRepository.findByUser(UID))
                .thenReturn(Flux.just(
                        PlanTestFixtures.counter(
                                PlanWindowKind.FIVE_HOUR,
                                fiveWindow.windowKey(),
                                fiveWindow.startsAt(),
                                fiveWindow.endsAt(),
                                FIVE_HOUR_LIMIT,
                                1_200_000L,
                                50_000L),
                        PlanTestFixtures.counter(
                                PlanWindowKind.WEEKLY,
                                weekWindow.windowKey(),
                                weekWindow.startsAt(),
                                weekWindow.endsAt(),
                                WEEKLY_LIMIT,
                                5_000_000L,
                                100_000L)));

        StepVerifier.create(service.usage(UID))
                .assertNext(view -> {
                    assertThat(view.plan().name()).isEqualTo("standard");
                    assertThat(view.plan().matchedBy()).isEqualTo("User/" + UID);
                    assertThat(view.fiveHour().state()).isEqualTo("ACTIVE");
                    assertThat(view.fiveHour().used()).isEqualTo(1_200_000L);
                    assertThat(view.fiveHour().reserved()).isEqualTo(50_000L);
                    assertThat(view.fiveHour().remaining()).isEqualTo(FIVE_HOUR_LIMIT - 1_250_000L);
                    assertThat(view.fiveHour().startsAt()).isEqualTo(fiveWindow.startsAt());
                    assertThat(view.weekly().state()).isEqualTo("ACTIVE");
                    assertThat(view.weekly().used()).isEqualTo(5_000_000L);
                    assertThat(view.weekly().remaining()).isEqualTo(WEEKLY_LIMIT - 5_100_000L);
                    assertThat(view.rateLimit().maxConcurrency()).isZero();
                    assertThat(view.rateLimit().maxRpm()).isZero();
                    assertThat(view.vouchers().fiveHour().total()).isEqualTo(2);
                    assertThat(view.vouchers().weekly().total()).isEqualTo(1);
                })
                .verifyComplete();
    }

    @Test
    void projectsUpcomingWeeklyWindowWhenNoCounterRowExists() {
        when(counterRepository.findByUser(UID)).thenReturn(Flux.empty());

        StepVerifier.create(service.usage(UID))
                .assertNext(view -> {
                    assertThat(view.fiveHour().state()).isEqualTo("IDLE");
                    assertThat(view.fiveHour().used()).isZero();
                    assertThat(view.fiveHour().reserved()).isZero();
                    assertThat(view.fiveHour().remaining()).isEqualTo(FIVE_HOUR_LIMIT);
                    assertThat(view.fiveHour().startsAt()).isNull();
                    assertThat(view.fiveHour().endsAt()).isNull();
                    assertThat(view.weekly().state()).isEqualTo("IDLE");
                    assertThat(view.weekly().used()).isZero();
                    assertThat(view.weekly().remaining()).isEqualTo(WEEKLY_LIMIT);
                    assertThat(view.weekly().startsAt()).isEqualTo(weekWindow.startsAt());
                    assertThat(view.weekly().endsAt()).isEqualTo(weekWindow.endsAt());
                })
                .verifyComplete();
    }

    @Test
    void treatsAnExpiredFiveHourWindowAsIdle() {
        when(counterRepository.findByUser(UID))
                .thenReturn(Flux.just(PlanTestFixtures.counter(
                        PlanWindowKind.FIVE_HOUR,
                        "5h:old",
                        PlanTestFixtures.NOW.minusSeconds(6 * 3600),
                        PlanTestFixtures.NOW.minusSeconds(3600),
                        FIVE_HOUR_LIMIT,
                        2_000_000L,
                        0)));

        StepVerifier.create(service.usage(UID))
                .assertNext(view -> {
                    assertThat(view.fiveHour().state()).isEqualTo("IDLE");
                    assertThat(view.fiveHour().used()).isZero();
                    assertThat(view.fiveHour().remaining()).isEqualTo(FIVE_HOUR_LIMIT);
                })
                .verifyComplete();
    }

    @Test
    void ignoresUsageFromAPreviousWeeklyWindow() {
        when(counterRepository.findByUser(UID))
                .thenReturn(Flux.just(PlanTestFixtures.counter(
                        PlanWindowKind.WEEKLY,
                        "week:2026-11-09@Asia/Shanghai",
                        Instant.parse("2026-11-08T16:00:00Z"),
                        Instant.parse("2026-11-15T16:00:00Z"),
                        WEEKLY_LIMIT,
                        4_000_000L,
                        0)));

        StepVerifier.create(service.usage(UID))
                .assertNext(view -> {
                    assertThat(view.weekly().state()).isEqualTo("IDLE");
                    assertThat(view.weekly().used()).isZero();
                    assertThat(view.weekly().remaining()).isEqualTo(WEEKLY_LIMIT);
                    assertThat(view.weekly().startsAt()).isEqualTo(weekWindow.startsAt());
                })
                .verifyComplete();
    }

    @Test
    void projectsVoucherAnchoredWeeklyWindows() {
        var anchor = Instant.parse("2026-11-18T02:00:00Z");
        var row = new PlanCounterRow(
                UID,
                "WEEKLY",
                PlanWindowClock.weekKey(anchor),
                anchor,
                anchor.plusSeconds(7 * 24 * 3600),
                WEEKLY_LIMIT,
                2_000_000L,
                0,
                PlanWindowClock.ANCHOR_VOUCHER,
                0);
        when(counterRepository.findByUser(UID)).thenReturn(Flux.just(row));

        StepVerifier.create(service.usage(UID))
                .assertNext(view -> {
                    assertThat(view.weekly().state()).isEqualTo("ACTIVE");
                    assertThat(view.weekly().used()).isEqualTo(2_000_000L);
                    assertThat(view.weekly().startsAt()).isEqualTo(anchor);
                    assertThat(view.weekly().endsAt()).isEqualTo(anchor.plusSeconds(7 * 24 * 3600));
                })
                .verifyComplete();
    }
}