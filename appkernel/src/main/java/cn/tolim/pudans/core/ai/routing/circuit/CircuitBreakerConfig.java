package cn.tolim.pudans.core.ai.routing.circuit;

import java.time.Duration;
import java.util.Objects;

public record CircuitBreakerConfig(
        int failureThreshold,
        int successThreshold,
        Duration openDuration,
        Duration maxOpenDuration,
        long maxLatencyMillis) {

    public CircuitBreakerConfig {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold 必须大于 0");
        }
        if (successThreshold < 1) {
            throw new IllegalArgumentException("successThreshold 必须大于 0");
        }
        requirePositive(openDuration, "openDuration");
        requirePositive(maxOpenDuration, "maxOpenDuration");
        if (maxOpenDuration.compareTo(openDuration) < 0) {
            throw new IllegalArgumentException("maxOpenDuration 不能小于 openDuration");
        }
        if (maxLatencyMillis < 1) {
            throw new IllegalArgumentException("maxLatencyMillis 必须大于 0");
        }
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name);
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " 必须大于 0");
        }
    }
}
