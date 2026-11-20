package cn.tolim.pudans.core.ai.harness.observability;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongCounterBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtelHarnessObservabilityTest {

    @Mock
    OpenTelemetry openTelemetry;

    @Mock
    Tracer tracer;

    @Mock
    Meter meter;

    @Mock
    SpanBuilder spanBuilder;

    @Mock
    Span span;

    private OtelHarnessObservability observability;

    @BeforeEach
    void setUp() {
        lenient().when(openTelemetry.getTracer(anyString())).thenReturn(tracer);
        lenient().when(openTelemetry.getMeter(anyString())).thenReturn(meter);
        observability = new OtelHarnessObservability(openTelemetry);
    }

    @Test
    void startsAndEndsTheSameSpan() {
        when(tracer.spanBuilder("tool")).thenReturn(spanBuilder);
        when(spanBuilder.startSpan()).thenReturn(span);
        when(spanBuilder.setAttribute(any(AttributeKey.class), any())).thenReturn(spanBuilder);
        var started = HarnessSpan.started(
                "span-1", null, HarnessSpanKind.TOOL, "tool", "run-1",
                Map.of("tool.name", "search"), Instant.now());

        observability.spanStarted(started);
        observability.spanEnded(started.ended(HarnessSpanStatus.OK, null, Instant.now().plusSeconds(1)));

        verify(span).setStatus(StatusCode.OK);
        verify(span).end();
    }

    @Test
    void emitsCountersThroughTheOtelMeter() {
        var builder = mock(LongCounterBuilder.class);
        var counter = mock(LongCounter.class);
        when(meter.counterBuilder("tool.calls")).thenReturn(builder);
        when(builder.build()).thenReturn(counter);

        observability.metric(new HarnessMetric(
                "tool.calls", HarnessMetric.Kind.COUNTER, 3, Map.of("tool", "search")));

        verify(counter).add(eq(3L), any(Attributes.class));
    }
}