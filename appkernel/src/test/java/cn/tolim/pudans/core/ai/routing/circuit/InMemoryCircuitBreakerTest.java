package cn.tolim.pudans.core.ai.routing.circuit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InMemoryCircuitBreakerTest {

    private static final Instant START = Instant.parse("2026-11-19T00:00:00Z");

    private static final CircuitBreakerConfig DEFAULT_CONFIG =
            new CircuitBreakerConfig(5, 3, Duration.ofSeconds(30), Duration.ofMinutes(10), 30_000);

    @Test
    void startsClosedWithFullHealth() {
        var breaker = new InMemoryCircuitBreaker(DEFAULT_CONFIG);

        assertThat(breaker.state("p")).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.isOpen("p")).isFalse();
        assertThat(breaker.healthScore("p")).isEqualTo(100);
    }

    @Test
    void opensAfterConfiguredFailureThreshold() {
        var config = new CircuitBreakerConfig(2, 1, Duration.ofSeconds(30), Duration.ofMinutes(1), 30_000);
        var breaker = new InMemoryCircuitBreaker(config);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        assertThat(breaker.state("p")).isEqualTo(CircuitState.CLOSED);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        assertThat(breaker.state("p")).isEqualTo(CircuitState.OPEN);
        assertThat(breaker.isOpen("p")).isTrue();
    }

    @Test
    void transitionsToHalfOpenAfterOpenDuration() {
        var clock = new MutableClock(START);
        var config = new CircuitBreakerConfig(1, 1, Duration.ofSeconds(10), Duration.ofMinutes(1), 30_000);
        var breaker = new InMemoryCircuitBreaker(config, clock);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        assertThat(breaker.state("p")).isEqualTo(CircuitState.OPEN);

        clock.advance(Duration.ofSeconds(9));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.OPEN);

        clock.advance(Duration.ofSeconds(1));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);
    }

    @Test
    void closesAfterSuccessThresholdInHalfOpen() {
        var clock = new MutableClock(START);
        var config = new CircuitBreakerConfig(1, 2, Duration.ofSeconds(10), Duration.ofMinutes(1), 30_000);
        var breaker = new InMemoryCircuitBreaker(config, clock);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        clock.advance(Duration.ofSeconds(10));

        breaker.recordSuccess("p");
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);

        breaker.recordSuccess("p");
        assertThat(breaker.state("p")).isEqualTo(CircuitState.CLOSED);
        assertThat(breaker.healthScore("p")).isEqualTo(100);
    }

    @Test
    void reopensWhenHalfOpenProbeFails() {
        var clock = new MutableClock(START);
        var config = new CircuitBreakerConfig(1, 1, Duration.ofSeconds(10), Duration.ofMinutes(1), 30_000);
        var breaker = new InMemoryCircuitBreaker(config, clock);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        clock.advance(Duration.ofSeconds(10));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        assertThat(breaker.state("p")).isEqualTo(CircuitState.OPEN);

        clock.advance(Duration.ofSeconds(10));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.OPEN);

        clock.advance(Duration.ofSeconds(10));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);
    }

    @Test
    void usesExplicitRestUntil() {
        var clock = new MutableClock(START);
        var config = new CircuitBreakerConfig(1, 1, Duration.ofSeconds(10), Duration.ofMinutes(1), 30_000);
        var breaker = new InMemoryCircuitBreaker(config, clock);

        breaker.recordFailure("p", CircuitFailure.until(START.plus(Duration.ofMinutes(5))));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.OPEN);

        clock.advance(Duration.ofMinutes(4));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.OPEN);

        clock.advance(Duration.ofMinutes(1));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);
    }

    @Test
    void capsExponentialBackoffAtMaximumOpenDuration() {
        var clock = new MutableClock(START);
        var config = new CircuitBreakerConfig(1, 1, Duration.ofSeconds(1), Duration.ofSeconds(4), 30_000);
        var breaker = new InMemoryCircuitBreaker(config, clock);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        clock.advance(Duration.ofSeconds(1));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        clock.advance(Duration.ofMillis(1900));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.OPEN);
        clock.advance(Duration.ofMillis(100));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        clock.advance(Duration.ofSeconds(4));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);

        breaker.recordFailure("p", CircuitFailure.defaultFailure());
        clock.advance(Duration.ofSeconds(4));
        assertThat(breaker.state("p")).isEqualTo(CircuitState.HALF_OPEN);
    }

    @Test
    void healthScoreCombinesSuccessLatencyAndCircuitPenalty() {
        var openConfig = new CircuitBreakerConfig(1, 1, Duration.ofSeconds(10), Duration.ofMinutes(1), 30_000);
        var openBreaker = new InMemoryCircuitBreaker(openConfig);
        openBreaker.recordSuccess("p", 30_000);
        openBreaker.recordFailure("p", CircuitFailure.defaultFailure());
        assertThat(openBreaker.healthScore("p")).isEqualTo(25);

        var closedConfig = new CircuitBreakerConfig(5, 1, Duration.ofSeconds(10), Duration.ofMinutes(1), 30_000);
        var closedBreaker = new InMemoryCircuitBreaker(closedConfig);
        closedBreaker.recordSuccess("p", 30_000);
        closedBreaker.recordFailure("p", CircuitFailure.defaultFailure());
        assertThat(closedBreaker.healthScore("p")).isEqualTo(45);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private final ZoneId zone;

        private MutableClock(Instant instant) {
            this(instant, ZoneOffset.UTC);
        }

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new MutableClock(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
