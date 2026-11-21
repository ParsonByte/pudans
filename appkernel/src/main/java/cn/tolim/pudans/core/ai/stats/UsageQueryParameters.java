package cn.tolim.pudans.core.ai.stats;

import cn.tolim.pudans.infra.exception.ApiException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.ServerRequest;

public final class UsageQueryParameters {

    private UsageQueryParameters() {}

    public static int limit(ServerRequest request) {
        return intValue(
                request,
                "limit",
                AiUsage.DEFAULT_LIMIT,
                1,
                AiUsage.MAX_LIMIT,
                "limit 必须在 1 到 " + AiUsage.MAX_LIMIT + " 之间。",
                "limit 必须是整数。");
    }

    public static int intValue(
            ServerRequest request,
            String name,
            int defaultValue,
            int min,
            int max,
            String rangeMessage,
            String formatMessage) {
        var value = request.queryParam(name).map(String::trim).filter(StringUtils::hasText).orElse(null);
        if (value == null) {
            return defaultValue;
        }
        try {
            var parsed = Integer.parseInt(value);
            if (parsed < min || parsed > max) {
                throw new ApiException(HttpStatus.BAD_REQUEST, AiUsageErrorCodes.PARAMETER_INVALID, rangeMessage);
            }
            return parsed;
        } catch (NumberFormatException error) {
            throw new ApiException(HttpStatus.BAD_REQUEST, AiUsageErrorCodes.PARAMETER_INVALID, formatMessage);
        }
    }

    public static long beforeId(ServerRequest request) {
        var value = request.queryParam("beforeId").map(String::trim).filter(StringUtils::hasText).orElse(null);
        if (value == null) {
            return AiUsage.NO_CURSOR;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, AiUsageErrorCodes.CURSOR_INVALID, "beforeId 必须是整数。");
        }
    }

    public static @Nullable Boolean bool(ServerRequest request, String name) {
        var value = request.queryParam(name).map(String::trim).filter(StringUtils::hasText).orElse(null);
        if (value == null) {
            return null;
        }
        if ("true".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        throw new ApiException(
                HttpStatus.BAD_REQUEST,
                AiUsageErrorCodes.PARAMETER_INVALID,
                "查询参数 '" + name + "' 必须是布尔值。");
    }

    public static @Nullable LocalDate date(ServerRequest request, String name) {
        var value = request.queryParam(name).map(String::trim).filter(StringUtils::hasText).orElse(null);
        if (value == null) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException error) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    AiUsageErrorCodes.DATE_INVALID,
                    "查询参数 '" + name + "' 必须是 ISO 日期。");
        }
    }

    public static @Nullable String text(ServerRequest request, String name) {
        return request.queryParam(name).map(String::trim).filter(StringUtils::hasText).orElse(null);
    }
}