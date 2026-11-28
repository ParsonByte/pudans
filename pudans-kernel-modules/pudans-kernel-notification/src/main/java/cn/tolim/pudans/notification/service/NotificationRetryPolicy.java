package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.configuration.NotificationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

// Calculates retry limits and exponential backoff timestamps.
@Component
public class NotificationRetryPolicy {

    private final NotificationProperties properties;

    public NotificationRetryPolicy(NotificationProperties properties) {
        this.properties = properties;
    }

    public int resolveMaxAttempts(Integer requested) {
        int configured = requested == null ? properties.getDefaultMaxAttempts() : requested;
        if (configured < 1) {
            return 1;
        }
        return Math.min(configured, 100);
    }

    public LocalDateTime nextRetryAt(int attempts) {
        NotificationProperties.Retry retry = properties.getRetry();
        double multiplier = retry.getBackoffMultiplier() <= 0 ? 1.0D : retry.getBackoffMultiplier();
        double delay = retry.getInitialDelaySeconds() * Math.pow(multiplier, Math.max(0, attempts - 1));
        long capped = Math.min((long) delay, retry.getMaxDelaySeconds());
        return LocalDateTime.now().plusSeconds(Math.max(1L, capped));
    }

    public boolean exhausted(int attempts, int maxAttempts) {
        return attempts >= maxAttempts;
    }
}