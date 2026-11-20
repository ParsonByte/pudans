package cn.tolim.pudans.core.ai.harness.observability;

public final class NoopHarnessObservability implements HarnessObservability {

    public static final NoopHarnessObservability INSTANCE = new NoopHarnessObservability();

    private NoopHarnessObservability() {}

    @Override
    public void spanStarted(HarnessSpan span) {}

    @Override
    public void spanEnded(HarnessSpan span) {}

    @Override
    public void metric(HarnessMetric metric) {}
}