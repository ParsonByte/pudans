package cn.tolim.pudans.core.ai.harness.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

// 原生 OpenTelemetry 导出；Span 按 harness 标识留存到结束事件再补属性
public final class OtelHarnessObservability implements HarnessObservability {

    private static final AttributeKey<String> RUN_ID = AttributeKey.stringKey("ai.harness.run_id");

    private static final AttributeKey<String> SPAN_KIND = AttributeKey.stringKey("ai.harness.span.kind");

    private static final AttributeKey<String> ERROR_TYPE = AttributeKey.stringKey("error.type");

    private final Tracer tracer;

    private final Meter meter;

    private final ConcurrentMap<String, Span> spans = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, LongCounter> counters = new ConcurrentHashMap<>();

    private final ConcurrentMap<String, DoubleHistogram> histograms = new ConcurrentHashMap<>();

    public OtelHarnessObservability(OpenTelemetry openTelemetry) {
        this.tracer = openTelemetry.getTracer("cn.tolim.pudans.ai.harness");
        this.meter = openTelemetry.getMeter("cn.tolim.pudans.ai.harness");
    }

    @Override
    public void spanStarted(HarnessSpan span) {
        var builder = tracer
                .spanBuilder(span.name())
                .setAttribute(RUN_ID, span.runId())
                .setAttribute(SPAN_KIND, span.kind().name());
        applyAttributes(builder::setAttribute, span.attributes());
        var started = builder.startSpan();
        spans.put(span.spanId(), started);
    }

    @Override
    public void spanEnded(HarnessSpan span) {
        var stored = spans.remove(span.spanId());
        var target = stored == null ? startForEnded(span) : stored;
        applyAttributes(target::setAttribute, span.attributes());
        if (span.status() == HarnessSpanStatus.ERROR || span.status() == HarnessSpanStatus.CANCELLED) {
            target.setStatus(StatusCode.ERROR);
            if (span.errorType() != null) {
                target.setAttribute(ERROR_TYPE, span.errorType());
            }
        } else if (span.status() == HarnessSpanStatus.OK) {
            target.setStatus(StatusCode.OK);
        }
        target.setAttribute("ai.harness.status", span.status().name());
        target.end();
    }

    private Span startForEnded(HarnessSpan span) {
        var builder = tracer
                .spanBuilder(span.name())
                .setStartTimestamp(span.startedAt())
                .setAttribute(RUN_ID, span.runId())
                .setAttribute(SPAN_KIND, span.kind().name());
        applyAttributes(builder::setAttribute, span.attributes());
        return builder.startSpan();
    }

    @Override
    public void metric(HarnessMetric metric) {
        var attributes = attributes(metric.tags());
        switch (metric.kind()) {
            case COUNTER -> counter(metric).add(Math.max(0, Math.round(metric.value())), attributes);
            case TIMER -> histogram(metric).record(metric.value(), attributes);
        }
    }

    private LongCounter counter(HarnessMetric metric) {
        return counters.computeIfAbsent(metric.name(), name -> meter.counterBuilder(name).build());
    }

    private DoubleHistogram histogram(HarnessMetric metric) {
        return histograms.computeIfAbsent(
                metric.name(),
                name -> meter.histogramBuilder(name).setUnit("ms").build());
    }

    private static Attributes attributes(Map<String, String> tags) {
        var builder = Attributes.builder();
        applyAttributes(builder::put, tags);
        return builder.build();
    }

    private static void applyAttributes(
            java.util.function.BiConsumer<AttributeKey<String>, String> sink,
            Map<String, String> attributes) {
        attributes.forEach((key, value) -> {
            if (key != null && value != null) {
                sink.accept(AttributeKey.stringKey(key), value);
            }
        });
    }
}