package cn.tolim.pudans.infra.utils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;

public final class RangeDays {

    public static final int DEFAULT = 7;

    private static final int MIN = 1;

    private static final int MAX = 90;

    private RangeDays() {}

    // 解析并校验统一的统计天数参数。
    public static int resolve(ServerRequest request) {
        var value = request.queryParam("days").orElse(null);
        if (!StringUtils.hasText(value)) {
            return DEFAULT;
        }
        try {
            var days = Integer.parseInt(value);
            if (days < MIN || days > MAX) {
                throw new ServerWebInputException("查询参数 'days' 必须在 1 到 90 之间。");
            }
            return days;
        } catch (NumberFormatException e) {
            throw new ServerWebInputException("查询参数 'days' 必须是整数。");
        }
    }

    // 解析可选的统计结束时刻，缺省为当前时刻。
    public static Instant resolveEnd(ServerRequest request) {
        var value = request.queryParam("to").orElse(null);
        if (!StringUtils.hasText(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            throw new ServerWebInputException("查询参数 'to' 必须是 ISO-8601 时间。");
        }
    }
}
