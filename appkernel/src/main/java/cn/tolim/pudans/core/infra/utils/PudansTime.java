package cn.tolim.pudans.core.infra.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

public final class PudansTime {

    public static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private PudansTime() {}

    public static Clock systemClock() {
        return Clock.system(BEIJING);
    }

    public static void applySystemTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(BEIJING));
    }

    public static LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, BEIJING);
    }

    public static Instant startOfDay(Instant instant) {
        return startOfDay(dateOf(instant));
    }

    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(BEIJING).toInstant();
    }
}