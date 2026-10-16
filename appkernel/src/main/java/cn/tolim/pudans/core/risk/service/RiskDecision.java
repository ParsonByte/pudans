package cn.tolim.pudans.core.risk.service;

import java.time.Duration;

public record RiskDecision(boolean allowed, long count, Duration retryAfter, Duration blockFor) {

    public static RiskDecision permit() {
        return new RiskDecision(true, 0L, Duration.ZERO, Duration.ZERO);
    }

    public static RiskDecision parse(String value, long blockSeconds) {
        if (value == null || value.isBlank()) {
            return permit();
        }
        var parts = value.split("\\|");
        var allowed = parts.length > 0 && "1".equals(parts[0]);
        var count = parseLong(parts, 1);
        var retryMillis = parseLong(parts, 2);
        return new RiskDecision(
                allowed, count, Duration.ofMillis(Math.max(0L, retryMillis)),
                Duration.ofSeconds(Math.max(0L, blockSeconds)));
    }

    private static long parseLong(String[] parts, int index) {
        if (parts.length <= index) {
            return 0L;
        }
        try {
            return (long) Double.parseDouble(parts[index]);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
