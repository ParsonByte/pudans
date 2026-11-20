package cn.tolim.pudans.core.ai.harness.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MicrometerHarnessObservability implements HarnessObservability {

    private static final String SPAN_TIMER = "ai.harness.span.duration";

    private final MeterRegistry registry;

    @Override
    public void spanStarted(HarnessSpan span) {}

    @Override
    public void spanEnded(HarnessSpan span) {
        if (span.endedAt() == null) {
            return;
        }
        io.micrometer.core.instrument.Timer.builder(SPAN_TIMER)
                .tags(tags(Map.of(
                        "kind", span.kind().name(),
                        "status", span.status().name())))
                .register(registry)
                .record(Duration.between(span.startedAt(), span.endedAt()));
    }

    @Override
    public void metric(HarnessMetric metric) {
        switch (metric.kind()) {
            case COUNTER -> registry
                    .counter(metric.name(), tags(metric.tags()))
                    .increment(metric.value());
            case TIMER -> io.micrometer.core.instrument.Timer.builder(metric.name())
                    .tags(tags(metric.tags()))
                    .register(registry)
                    .record(Duration.ofMillis(Math.max(0, Math.round(metric.value()))));
        }
    }

    private static List<Tag> tags(Map<String, String> tags) {
        return tags.entrySet().stream()
                .map(entry -> Tag.of(entry.getKey(), entry.getValue() == null ? "" : entry.getValue()))
                .toList();
    }
}