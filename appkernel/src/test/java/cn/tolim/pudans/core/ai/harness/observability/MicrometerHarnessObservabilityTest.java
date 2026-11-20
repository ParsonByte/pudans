package cn.tolim.pudans.core.ai.harness.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class MicrometerHarnessObservabilityTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final MicrometerHarnessObservability observability =
            new MicrometerHarnessObservability(registry);

    @Test
    void recordsCountersTimersAndSpans() {
        observability.metric(new HarnessMetric(
                "ai.test.counter", HarnessMetric.Kind.COUNTER, 2, Map.of("k", "v")));
        observability.metric(new HarnessMetric(
                "ai.test.timer", HarnessMetric.Kind.TIMER, 150, Map.of("k", "v")));
        var started = Instant.parse("2026-01-01T00:00:00Z");
        observability.spanEnded(HarnessSpan.started(
                        "span-1",
                        null,
                        HarnessSpanKind.AGENT,
                        "agent",
                        "run-1",
                        Map.of(),
                        started)
                .ended(HarnessSpanStatus.OK, null, started.plusSeconds(1)));

        assertThat(registry.get("ai.test.counter").tag("k", "v").counter().count())
                .isEqualTo(2.0);
        assertThat(registry.get("ai.test.timer").tag("k", "v").timer().count())
                .isEqualTo(1);
        assertThat(registry.get("ai.harness.span.duration")
                        .tag("kind", "AGENT")
                        .tag("status", "OK")
                        .timer()
                        .totalTime(TimeUnit.MILLISECONDS))
                .isEqualTo(1000);
    }
}