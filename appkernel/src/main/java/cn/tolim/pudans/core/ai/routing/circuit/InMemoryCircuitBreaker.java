package cn.tolim.pudans.core.ai.routing.circuit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;

public class InMemoryCircuitBreaker implements CircuitBreaker {

    private static final double SUCCESS_RATE_WEIGHT = 50;

    private static final double LATENCY_WEIGHT = 30;

    private static final double CIRCUIT_WEIGHT = 20;

    private static final double HALF_OPEN_PENALTY = 0.5;

    private static final double OPEN_PENALTY = 1;

    private final CircuitBreakerConfig config;

    private final Clock clock;

    private final ConcurrentMap<String, Circuit> circuits = new ConcurrentHashMap<>();

    public InMemoryCircuitBreaker(CircuitBreakerConfig config) {
        this(config, Clock.systemUTC());
    }

    public InMemoryCircuitBreaker(CircuitBreakerConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CircuitState state(String key) {
        var circuit = circuit(key);
        synchronized (circuit) {
            transitionIfReady(circuit);
            return circuit.state;
        }
    }

    @Override
    public void recordSuccess(String key, long latencyMillis) {
        var circuit = circuit(key);
        synchronized (circuit) {
            transitionIfReady(circuit);
            if (latencyMillis >= 0) {
                circuit.latencyTotalMillis += latencyMillis;
                circuit.latencySamples++;
            }
            switch (circuit.state) {
                case CLOSED -> {
                    circuit.consecutiveFailures = 0;
                    circuit.successes++;
                }
                case HALF_OPEN -> {
                    circuit.halfOpenSuccesses++;
                    if (circuit.halfOpenSuccesses >= config.successThreshold()) {
                        close(circuit);
                    }
                }
                case OPEN -> {
                    // 冷却期内的迟到成功结果不能提前关闭熔断
                }
            }
        }
    }

    @Override
    public void recordFailure(String key, CircuitFailure failure) {
        var circuit = circuit(key);
        synchronized (circuit) {
            transitionIfReady(circuit);
            circuit.failures++;
            switch (circuit.state) {
                case OPEN -> {
                    // 冷却期内的重复失败不延长当前冷却时间
                }
                case HALF_OPEN -> open(circuit, failure);
                case CLOSED -> {
                    circuit.consecutiveFailures++;
                    if (circuit.consecutiveFailures >= config.failureThreshold()) {
                        open(circuit, failure);
                    }
                }
            }
        }
    }

    @Override
    public double healthScore(String key) {
        var circuit = circuit(key);
        synchronized (circuit) {
            transitionIfReady(circuit);
            long samples = circuit.successes + circuit.failures;
            double successRate = samples == 0 ? 1 : (double) circuit.successes / samples;
            double latency = circuit.latencySamples == 0
                    ? 0
                    : (double) circuit.latencyTotalMillis / circuit.latencySamples;
            double latencyFactor = 1 - Math.min(1, latency / config.maxLatencyMillis());
            double penalty = switch (circuit.state) {
                case CLOSED -> 0;
                case HALF_OPEN -> HALF_OPEN_PENALTY;
                case OPEN -> OPEN_PENALTY;
            };
            return successRate * SUCCESS_RATE_WEIGHT
                    + latencyFactor * LATENCY_WEIGHT
                    + (1 - penalty) * CIRCUIT_WEIGHT;
        }
    }

    private Circuit circuit(String key) {
        var normalized = Objects.requireNonNull(key, "key");
        return circuits.computeIfAbsent(normalized, ignored -> new Circuit());
    }

    // 跨副本同步使用的只读快照，不暴露内部计数结构
    java.util.Set<String> keys() {
        return java.util.Set.copyOf(circuits.keySet());
    }

    @Nullable
    Instant retryAt(String key) {
        var circuit = circuit(key);
        synchronized (circuit) {
            transitionIfReady(circuit);
            return circuit.state == CircuitState.OPEN ? circuit.retryAt : null;
        }
    }

    // 其他副本已熔断时，本地至少保持到相同冷却时间
    void forceOpen(String key, @Nullable Instant retryAt) {
        if (retryAt == null) {
            return;
        }
        var circuit = circuit(key);
        synchronized (circuit) {
            transitionIfReady(circuit);
            if (circuit.state == CircuitState.OPEN && circuit.retryAt != null && !retryAt.isAfter(circuit.retryAt)) {
                return;
            }
            circuit.state = CircuitState.OPEN;
            circuit.halfOpenSuccesses = 0;
            circuit.consecutiveFailures = 0;
            circuit.retryAt = retryAt;
        }
    }

    private void transitionIfReady(Circuit circuit) {
        if (circuit.state == CircuitState.OPEN && !clock.instant().isBefore(circuit.retryAt)) {
            circuit.state = CircuitState.HALF_OPEN;
            circuit.halfOpenSuccesses = 0;
        }
    }

    private void open(Circuit circuit, CircuitFailure failure) {
        var now = clock.instant();
        circuit.state = CircuitState.OPEN;
        circuit.halfOpenSuccesses = 0;
        circuit.consecutiveFailures = 0;
        circuit.openings++;
        circuit.retryAt = now.plus(restDuration(circuit.openings, failure, now));
    }

    private Duration restDuration(int openings, CircuitFailure failure, Instant now) {
        if (failure != null && failure.restUntil() != null && failure.restUntil().isAfter(now)) {
            return Duration.between(now, failure.restUntil());
        }
        var duration = config.openDuration();
        for (int index = 1; index < openings; index++) {
            if (duration.compareTo(config.maxOpenDuration()) >= 0) {
                return config.maxOpenDuration();
            }
            duration = duration.multipliedBy(2);
        }
        return duration.compareTo(config.maxOpenDuration()) > 0 ? config.maxOpenDuration() : duration;
    }

    private void close(Circuit circuit) {
        circuit.state = CircuitState.CLOSED;
        circuit.consecutiveFailures = 0;
        circuit.halfOpenSuccesses = 0;
        circuit.openings = 0;
        circuit.successes = 0;
        circuit.failures = 0;
        circuit.latencyTotalMillis = 0;
        circuit.latencySamples = 0;
        circuit.retryAt = null;
    }

    private static final class Circuit {

        private CircuitState state = CircuitState.CLOSED;

        private int consecutiveFailures;

        private int halfOpenSuccesses;

        private int openings;

        private long successes;

        private long failures;

        private long latencyTotalMillis;

        private long latencySamples;

        private Instant retryAt;
    }
}
