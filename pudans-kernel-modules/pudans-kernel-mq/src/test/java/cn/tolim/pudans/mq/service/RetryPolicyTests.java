package cn.tolim.pudans.mq.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTests {

    @Test
    void appliesExponentialBackoff() {
        RetryPolicy policy = new RetryPolicy(1000, 60000, 2.0);

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(1000));
        assertThat(policy.delayForAttempt(2)).isEqualTo(Duration.ofMillis(2000));
        assertThat(policy.delayForAttempt(3)).isEqualTo(Duration.ofMillis(4000));
        assertThat(policy.delayForAttempt(4)).isEqualTo(Duration.ofMillis(8000));
    }

    @Test
    void capsDelayAtMaximum() {
        RetryPolicy policy = new RetryPolicy(1000, 5000, 2.0);

        assertThat(policy.delayForAttempt(1)).isEqualTo(Duration.ofMillis(1000));
        assertThat(policy.delayForAttempt(10)).isEqualTo(Duration.ofMillis(5000));
    }

    @Test
    void treatsNonPositiveAttemptAsFirstAttempt() {
        RetryPolicy policy = new RetryPolicy(250, 60000, 2.0);

        assertThat(policy.delayForAttempt(0)).isEqualTo(Duration.ofMillis(250));
        assertThat(policy.delayForAttempt(-5)).isEqualTo(Duration.ofMillis(250));
    }

    @Test
    void computesNextAttemptTimestamp() {
        RetryPolicy policy = new RetryPolicy(1000, 60000, 2.0);
        LocalDateTime now = LocalDateTime.of(2026, 1, 1, 12, 0);

        assertThat(policy.nextAttemptAt(now, 1)).isEqualTo(now.plusSeconds(1));
        assertThat(policy.nextAttemptAt(now, 3)).isEqualTo(now.plusSeconds(4));
    }

    @Test
    void normalizesInvalidConfiguration() {
        RetryPolicy policy = new RetryPolicy(0, 0, 0.5);

        assertThat(policy.getInitialDelayMillis()).isEqualTo(1);
        assertThat(policy.getMaxDelayMillis()).isEqualTo(1);
        assertThat(policy.getMultiplier()).isEqualTo(1.0);
    }
}