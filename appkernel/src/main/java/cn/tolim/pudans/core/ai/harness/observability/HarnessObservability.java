package cn.tolim.pudans.core.ai.harness.observability;

public interface HarnessObservability {

    void spanStarted(HarnessSpan span);

    void spanEnded(HarnessSpan span);

    void metric(HarnessMetric metric);
}