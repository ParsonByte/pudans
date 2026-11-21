package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.utils.PudansTime;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

final class UsageWindow {

    private static final int DEFAULT_RANGE_DAYS = 7;

    private static final int MAX_RANGE_DAYS = 366;

    private UsageWindow() {}

    static Window of(@Nullable LocalDate from, @Nullable LocalDate to, Clock clock) {
        var end = to == null ? LocalDate.now(clock.withZone(PudansTime.BEIJING)) : to;
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
                PudansTime.startOfDay(start),
                PudansTime.startOfDay(end.plusDays(1)));
    }

    record Window(LocalDate from, LocalDate to, Instant fromAt, Instant toAt) {}
}