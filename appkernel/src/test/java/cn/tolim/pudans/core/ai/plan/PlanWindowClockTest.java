package cn.tolim.pudans.core.ai.plan;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.core.extension.ai.AiPlan;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlanWindowClockTest {

    private static final Instant NOW = Instant.parse("2026-11-18T10:00:00Z");

    private PlanWindowClock clock;

    @BeforeEach
    void setUp() {
        clock = new PlanWindowClock();
        clock.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void opensAFreshFiveHourWindow() {
        var window = clock.fiveHour(NOW, null);

        assertThat(window.windowKey()).isEqualTo("5h:2026-11-18T10:00:00Z");
        assertThat(window.startsAt()).isEqualTo(NOW);
        assertThat(window.endsAt()).isEqualTo(NOW.plusSeconds(5 * 3600));
    }

    @Test
    void keepsAnActiveFiveHourWindow() {
        var existing = new PlanWindowClock.ExistingWindow(
                "5h:2026-11-18T08:00:00Z",
                NOW.minusSeconds(2 * 3600),
                NOW.plusSeconds(3 * 3600),
                false);

        var window = clock.fiveHour(NOW, existing);

        assertThat(window.windowKey()).isEqualTo(existing.windowKey());
        assertThat(window.startsAt()).isEqualTo(existing.startsAt());
        assertThat(window.endsAt()).isEqualTo(existing.endsAt());
    }

    @Test
    void replacesAnExpiredFiveHourWindow() {
        var existing = new PlanWindowClock.ExistingWindow(
                "5h:2026-11-18T05:00:00Z",
                NOW.minusSeconds(5 * 3600),
                NOW,
                false);

        var window = clock.fiveHour(NOW, existing);

        assertThat(window.windowKey()).isEqualTo("5h:2026-11-18T10:00:00Z");
        assertThat(window.startsAt()).isEqualTo(NOW);
    }

    @Test
    void computesTheBeijingWeeklyWindow() {
        var window = clock.weekly(NOW, new AiPlan.Weekly(), null);

        assertThat(window.startsAt()).isEqualTo(Instant.parse("2026-11-15T16:00:00Z"));
        assertThat(window.endsAt()).isEqualTo(Instant.parse("2026-11-22T16:00:00Z"));
        assertThat(window.windowKey()).isEqualTo("week:2026-11-16@Asia/Shanghai");
    }

    @Test
    void startsANewWeeklyWindowOnTheResetBoundary() {
        var boundary = Instant.parse("2026-11-15T16:00:00Z");

        var window = clock.weekly(boundary, new AiPlan.Weekly(), null);

        assertThat(window.startsAt()).isEqualTo(boundary);
        assertThat(window.endsAt()).isEqualTo(boundary.plusSeconds(7 * 24 * 3600));
        assertThat(window.windowKey()).isEqualTo("week:2026-11-16@Asia/Shanghai");
    }

    @Test
    void keepsThePreviousWindowJustBeforeTheResetBoundary() {
        var window = clock.weekly(Instant.parse("2026-11-15T15:59:59Z"), new AiPlan.Weekly(), null);

        assertThat(window.startsAt()).isEqualTo(Instant.parse("2026-11-08T16:00:00Z"));
        assertThat(window.endsAt()).isEqualTo(Instant.parse("2026-11-15T16:00:00Z"));
    }

    @Test
    void honoursTheConfiguredResetHour() {
        var weekly = new AiPlan.Weekly();
        weekly.setResetDayOfWeek(DayOfWeek.MONDAY);
        weekly.setResetHour(10);

        var window = clock.weekly(NOW, weekly, null);

        assertThat(window.startsAt()).isEqualTo(Instant.parse("2026-11-16T02:00:00Z"));
        assertThat(window.endsAt()).isEqualTo(Instant.parse("2026-11-23T02:00:00Z"));
    }

    @Test
    void rollsBackWhenTheResetMomentIsStillAhead() {
        var weekly = new AiPlan.Weekly();
        weekly.setResetDayOfWeek(DayOfWeek.FRIDAY);
        weekly.setResetHour(10);

        var window = clock.weekly(NOW, weekly, null);

        assertThat(window.startsAt()).isEqualTo(Instant.parse("2026-11-13T02:00:00Z"));
        assertThat(window.endsAt()).isEqualTo(Instant.parse("2026-11-20T02:00:00Z"));
    }

    @Test
    void keepsTheVoucherWeeklyAnchor() {
        var anchor = Instant.parse("2026-11-18T02:00:00Z");
        var existing = new PlanWindowClock.ExistingWindow(
                "week:2026-11-18@Asia/Shanghai", anchor, anchor.plusSeconds(7 * 24 * 3600), true);

        var window = clock.weekly(Instant.parse("2026-11-20T10:00:00Z"), new AiPlan.Weekly(), existing);

        assertThat(window.startsAt()).isEqualTo(anchor);
        assertThat(window.endsAt()).isEqualTo(anchor.plusSeconds(7 * 24 * 3600));
        assertThat(window.windowKey()).isEqualTo("week:2026-11-18@Asia/Shanghai");
    }

    @Test
    void rollsTheVoucherAnchorForwardByWholeWeeks() {
        var anchor = Instant.parse("2026-11-18T02:00:00Z");
        var existing = new PlanWindowClock.ExistingWindow(
                "week:2026-11-18@Asia/Shanghai", anchor, anchor.plusSeconds(7 * 24 * 3600), true);

        var window = clock.weekly(anchor.plusSeconds(8 * 24 * 3600), new AiPlan.Weekly(), existing);

        assertThat(window.startsAt()).isEqualTo(anchor.plusSeconds(7 * 24 * 3600));
        assertThat(window.endsAt()).isEqualTo(anchor.plusSeconds(14 * 24 * 3600));
    }

    @Test
    void opensAWeeklyWindowAtTheExactVoucherAnchorRollover() {
        var anchor = Instant.parse("2026-11-18T02:00:00Z");
        var existing = new PlanWindowClock.ExistingWindow(
                "week:2026-11-18@Asia/Shanghai", anchor, anchor.plusSeconds(7 * 24 * 3600), true);

        var window = clock.weekly(anchor.plusSeconds(7 * 24 * 3600), new AiPlan.Weekly(), existing);

        assertThat(window.startsAt()).isEqualTo(anchor.plusSeconds(7 * 24 * 3600));
    }

    @Test
    void opensAResetWindowFromTheCurrentInstant() {
        var window = clock.weeklyReset(NOW);

        assertThat(window.windowKey()).isEqualTo("week:2026-11-18@Asia/Shanghai");
        assertThat(window.startsAt()).isEqualTo(NOW);
        assertThat(window.endsAt()).isEqualTo(NOW.plusSeconds(7 * 24 * 3600));
    }

    @Test
    void reportsTheFixedInstant() {
        assertThat(clock.now()).isEqualTo(NOW);
    }
}