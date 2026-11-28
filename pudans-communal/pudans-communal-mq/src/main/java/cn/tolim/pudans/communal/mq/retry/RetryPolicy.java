package cn.tolim.pudans.communal.mq.retry;

import java.time.Duration;

// Exponential backoff policy for message delivery attempts.
public record RetryPolicy(int maxAttempts, Duration initialBackoff, double multiplier, Duration maxBackoff) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be greater than or equal to 1");
        }
        if (initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must not be negative");
        }
        if (multiplier < 1.0d) {
            throw new IllegalArgumentException("multiplier must be greater than or equal to 1.0");
        }
        if (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must be greater than or equal to initialBackoff");
        }
    }

    public static RetryPolicy defaults() {
        return new RetryPolicy(3, Duration.ofMillis(500), 2.0d, Duration.ofSeconds(30));
    }

    public static RetryPolicy noDelay(int maxAttempts) {
        return new RetryPolicy(maxAttempts, Duration.ZERO, 1.0d, Duration.ZERO);
    }

    // Attempt is 1-based; the first retry uses the initial backoff.
    public Duration backoffForAttempt(int attempt) {
        if (attempt <= 1) {
            return initialBackoff;
        }
        double millis = initialBackoff.toMillis() * Math.pow(multiplier, attempt - 1);
        long cappedMillis = (long) Math.min(millis, maxBackoff.toMillis());
        return Duration.ofMillis(cappedMillis);
    }
}