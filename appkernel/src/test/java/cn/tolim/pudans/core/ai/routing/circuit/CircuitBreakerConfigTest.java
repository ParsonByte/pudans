package cn.tolim.pudans.core.ai.routing.circuit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CircuitBreakerConfigTest {

    @Test
    void rejectsNonPositiveThresholds() {
        assertThatThrownBy(() -> new CircuitBreakerConfig(0, 1, Duration.ofSeconds(1), Duration.ofSeconds(2), 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerConfig(1, 0, Duration.ofSeconds(1), Duration.ofSeconds(2), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveDurationsAndLatency() {
        assertThatThrownBy(() -> new CircuitBreakerConfig(1, 1, Duration.ZERO, Duration.ofSeconds(2), 1000))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreakerConfig(1, 1, Duration.ofSeconds(1), Duration.ofSeconds(2), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMaximumOpenDurationBelowOpenDuration() {
        assertThatThrownBy(() -> new CircuitBreakerConfig(1, 1, Duration.ofSeconds(2), Duration.ofSeconds(1), 1000))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
