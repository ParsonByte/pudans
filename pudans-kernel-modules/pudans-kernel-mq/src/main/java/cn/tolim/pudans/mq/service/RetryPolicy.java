package cn.tolim.pudans.mq.service;

import java.time.Duration;
import java.time.LocalDateTime;

public class RetryPolicy {

    private final long initialDelayMillis;

    private final long maxDelayMillis;

    private final double multiplier;

    public RetryPolicy(long initialDelayMillis, long maxDelayMillis, double multiplier) {
        this.initialDelayMillis = Math.max(1L, initialDelayMillis);
        this.maxDelayMillis = Math.max(this.initialDelayMillis, maxDelayMillis);
        this.multiplier = multiplier < 1.0D ? 1.0D : multiplier;
    }

    public Duration delayForAttempt(int attempt) {
        int normalizedAttempt = Math.max(attempt, 1);
        long delay = initialDelayMillis;
        for (int current = 1; current < normalizedAttempt && delay < maxDelayMillis; current++) {
            double next = delay * multiplier;
            delay = next >= maxDelayMillis ? maxDelayMillis : (long) Math.ceil(next);
        }
        return Duration.ofMillis(delay);
    }

    public LocalDateTime nextAttemptAt(LocalDateTime now, int attempt) {
        return now.plus(delayForAttempt(attempt));
    }

    public long getInitialDelayMillis() {
        return initialDelayMillis;
    }

    public long getMaxDelayMillis() {
        return maxDelayMillis;
    }

    public double getMultiplier() {
        return multiplier;
    }
}