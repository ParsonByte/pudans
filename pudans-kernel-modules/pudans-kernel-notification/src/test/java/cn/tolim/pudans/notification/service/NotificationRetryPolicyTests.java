package cn.tolim.pudans.notification.service;

import cn.tolim.pudans.notification.configuration.NotificationProperties;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationRetryPolicyTests {

    @Test
    void clampsMaxAttemptsToSupportedRange() {
        NotificationRetryPolicy policy = new NotificationRetryPolicy(new NotificationProperties());

        assertThat(policy.resolveMaxAttempts(null)).isEqualTo(3);
        assertThat(policy.resolveMaxAttempts(0)).isEqualTo(1);
        assertThat(policy.resolveMaxAttempts(7)).isEqualTo(7);
        assertThat(policy.resolveMaxAttempts(1_000)).isEqualTo(100);
    }

    @Test
    void growsRetryDelayUpToTheConfiguredMaximum() {
        NotificationProperties properties = new NotificationProperties();
        properties.getRetry().setInitialDelaySeconds(10);
        properties.getRetry().setBackoffMultiplier(2.0D);
        properties.getRetry().setMaxDelaySeconds(25);
        NotificationRetryPolicy policy = new NotificationRetryPolicy(properties);

        LocalDateTime first = policy.nextRetryAt(1);
        LocalDateTime third = policy.nextRetryAt(3);

        assertThat(first).isAfter(LocalDateTime.now().plusSeconds(5));
        assertThat(third).isBefore(LocalDateTime.now().plusSeconds(30));
        assertThat(policy.exhausted(3, 3)).isTrue();
        assertThat(policy.exhausted(2, 3)).isFalse();
    }
}