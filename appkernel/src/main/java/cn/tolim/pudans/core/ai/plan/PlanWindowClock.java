package cn.tolim.pudans.core.ai.plan;

import cn.tolim.pudans.contract.ai.resource.AiPlan;
import cn.tolim.pudans.infra.utils.PudansTime;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class PlanWindowClock {

    public static final String ANCHOR_PLAN = "PLAN";

    public static final String ANCHOR_VOUCHER = "VOUCHER";

    private static final Duration FIVE_HOUR = Duration.ofHours(5);

    private static final Duration WEEK = Duration.ofDays(7);

    private Clock clock = PudansTime.systemClock();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Instant now() {
        return clock.instant();
    }

    public PlanWindow fiveHour(Instant now, @Nullable ExistingWindow existing) {
        if (existing != null && existing.endsAt().isAfter(now)) {
            return existing.toWindow();
        }
        return fiveHourReset(now);
    }

    public PlanWindow fiveHourReset(Instant now) {
        return new PlanWindow(fiveHourKey(now), now, now.plus(FIVE_HOUR));
    }

    public PlanWindow weekly(Instant now, AiPlan.@Nullable Weekly weekly, @Nullable ExistingWindow existing) {
        if (existing != null && existing.voucherAnchor()) {
            return voucherWeekly(now, existing.startsAt());
        }
        return planWeekly(now, weekly);
    }

    public PlanWindow weeklyReset(Instant now) {
        return weeklyWindow(now, now.plus(WEEK));
    }

    public static String fiveHourKey(Instant startsAt) {
        return "5h:" + startsAt;
    }

    public static String weekKey(Instant startsAt) {
        var date = LocalDate.ofInstant(startsAt, PudansTime.BEIJING);
        return "week:" + date + "@" + PudansTime.BEIJING.getId();
    }

    private static PlanWindow planWeekly(Instant now, AiPlan.@Nullable Weekly weekly) {
        var resetDay = weekly == null || weekly.getResetDayOfWeek() == null
                ? DayOfWeek.MONDAY
                : weekly.getResetDayOfWeek();
        var resetHour = weekly == null ? 0 : weekly.getResetHour();
        var beijingNow = LocalDateTime.ofInstant(now, PudansTime.BEIJING);
        var boundary = beijingNow
                .toLocalDate()
                .with(TemporalAdjusters.previousOrSame(resetDay))
                .atTime(resetHour, 0)
                .atZone(PudansTime.BEIJING)
                .toInstant();
        var startsAt = boundary.isAfter(now) ? boundary.minus(WEEK) : boundary;
        return weeklyWindow(startsAt, startsAt.plus(WEEK));
    }

    private static PlanWindow voucherWeekly(Instant now, Instant anchor) {
        if (!anchor.isBefore(now)) {
            return weeklyWindow(anchor, anchor.plus(WEEK));
        }
        var periods = Duration.between(anchor, now).toSeconds() / WEEK.toSeconds();
        var startsAt = anchor.plusSeconds(periods * WEEK.toSeconds());
        return weeklyWindow(startsAt, startsAt.plus(WEEK));
    }

    private static PlanWindow weeklyWindow(Instant startsAt, Instant endsAt) {
        return new PlanWindow(weekKey(startsAt), startsAt, endsAt);
    }

    public record ExistingWindow(String windowKey, Instant startsAt, Instant endsAt, boolean voucherAnchor) {

        PlanWindow toWindow() {
            return new PlanWindow(windowKey, startsAt, endsAt);
        }
    }
}