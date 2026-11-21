package cn.tolim.pudans.infra.utils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.TimeZone;

public final class PudansTime {

    public static final ZoneId BEIJING = ZoneId.of("Asia/Shanghai");

    private PudansTime() {}

    // 使用平台固定时区提供可注入时钟。
    public static Clock systemClock() {
        return Clock.system(BEIJING);
    }

    // 将 JVM 默认时区固定为北京时间。
    public static void applySystemTimeZone() {
        TimeZone.setDefault(TimeZone.getTimeZone(BEIJING));
    }

    // 按北京时间提取日期。
    public static LocalDate dateOf(Instant instant) {
        return LocalDate.ofInstant(instant, BEIJING);
    }

    // 返回北京时间当天零点对应的时刻。
    public static Instant startOfDay(Instant instant) {
        return startOfDay(dateOf(instant));
    }

    // 指定日期在北京时间下的起始时刻。
    public static Instant startOfDay(LocalDate date) {
        return date.atStartOfDay(BEIJING).toInstant();
    }
}