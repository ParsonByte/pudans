package cn.tolim.pudans.core.ai.harness.observability;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryHarnessObservability implements HarnessObservability {

    private final List<HarnessSpan> spans = new CopyOnWriteArrayList<>();

    private final List<HarnessMetric> metrics = new CopyOnWriteArrayList<>();

    @Override
    public void spanStarted(HarnessSpan span) {
        spans.add(span);
    }

    @Override
    public void spanEnded(HarnessSpan span) {
        spans.add(span);
    }

    @Override
    public void metric(HarnessMetric metric) {
        metrics.add(metric);
    }

    public List<HarnessSpan> spans() {
        return List.copyOf(spans);
    }

    public List<HarnessMetric> metrics() {
        return List.copyOf(metrics);
    }
}