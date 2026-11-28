package cn.tolim.pudans.communal.mq.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RetryPolicyTests {

    @Test
    void firstAttemptUsesInitialBackoff() {
        RetryPolicy policy = new RetryPolicy(5, Duration.ofMillis(100), 2.0d, Duration.ofSeconds(2));

        assertThat(policy.backoffForAttempt(1)).isEqualTo(Duration.ofMillis(100));
    }

    @Test
    void backoffGrowsExponentiallyAndCapsAtMaximum() {
        RetryPolicy policy = new RetryPolicy(5, Duration.ofMillis(100), 2.0d, Duration.ofMillis(300));

        assertThat(policy.backoffForAttempt(2)).isEqualTo(Duration.ofMillis(200));
        assertThat(policy.backoffForAttempt(3)).isEqualTo(Duration.ofMillis(300));
        assertThat(policy.backoffForAttempt(4)).isEqualTo(Duration.ofMillis(300));
    }

    @Test
    void rejectsInvalidPolicy() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RetryPolicy(0, Duration.ZERO, 1.0d, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RetryPolicy(1, Duration.ofMillis(10), 0.5d, Duration.ofMillis(10)));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new RetryPolicy(1, Duration.ofMillis(10), 1.0d, Duration.ofMillis(5)));
    }

    @Test
    void noDelayPolicyNeverWaits() {
        RetryPolicy policy = RetryPolicy.noDelay(3);

        assertThat(policy.backoffForAttempt(2)).isEqualTo(Duration.ZERO);
    }
}