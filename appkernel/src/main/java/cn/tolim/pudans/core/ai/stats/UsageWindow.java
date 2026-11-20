package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.infra.exception.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

final class UsageWindow {

    static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Shanghai");

    private static final int DEFAULT_RANGE_DAYS = 7;

    private static final int MAX_RANGE_DAYS = 366;

    private UsageWindow() {}

    static Window of(@Nullable LocalDate from, @Nullable LocalDate to, Clock clock) {
        var end = to == null ? LocalDate.now(clock.withZone(SERVER_ZONE)) : to;
        var start = from == null ? end.minusDays(DEFAULT_RANGE_DAYS - 1L) : from;
        if (start.isAfter(end)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.DATE_RANGE_INVALID, "开始日期不能晚于结束日期。");
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_RANGE_DAYS) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.DATE_RANGE_INVALID, "日期范围不能超过 366 天。");
        }
        return new Window(
                start,
                end,
                start.atStartOfDay(SERVER_ZONE).toInstant(),
                end.plusDays(1).atStartOfDay(SERVER_ZONE).toInstant());
    }

    record Window(LocalDate from, LocalDate to, Instant fromAt, Instant toAt) {}
}