package cn.tolim.pudans.communal.log;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceIdFilterTests {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String OTHER_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String OTHER_SPAN_ID = "b7ad6b7169203331";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void echoesRequestIdAndPublishesTraceContext() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(new PudansLogProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Request-Id", "req-42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInside = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                traceInside.set(MDC.get(MdcKeys.TRACE_ID));

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-42");
        assertThat(traceInside.get()).matches("[0-9a-f]{32}");
        assertThat(TraceContext.traceId()).isEmpty();
    }

    @Test
    void reusesIncomingTraceparentWhenNoSpanIsActive() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(new PudansLogProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("traceparent", "00-" + TRACE_ID + "-" + SPAN_ID + "-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInside = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
                traceInside.set(MDC.get(MdcKeys.TRACE_ID));

        filter.doFilter(request, response, chain);

        assertThat(traceInside.get()).isEqualTo(TRACE_ID);
        assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f]{32}");
    }

    @Test
    void generatesRequestIdWhenIncomingValueIsInvalid() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(new PudansLogProperties());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Request-Id", "bad\nvalue");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
        });

        assertThat(response.getHeader("X-Request-Id")).matches("[0-9a-f]{32}");
    }

    @Test
    void usesCurrentTracerSpanInsteadOfIncomingTraceparent() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(new PudansLogProperties(),
                tracerProvider(tracerWithCurrentSpan(TRACE_ID, SPAN_ID)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-Request-Id", "req-42");
        request.addHeader("traceparent", "00-" + OTHER_TRACE_ID + "-" + OTHER_SPAN_ID + "-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInside = new AtomicReference<>();
        AtomicReference<String> spanInside = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            traceInside.set(MDC.get(MdcKeys.TRACE_ID));
            spanInside.set(MDC.get(MdcKeys.SPAN_ID));
        };

        filter.doFilter(request, response, chain);

        assertThat(traceInside.get()).isEqualTo(TRACE_ID);
        assertThat(spanInside.get()).isEqualTo(SPAN_ID);
        assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-42");
    }

    @Test
    void usesUnsampledTraceContextInsteadOfIncomingTraceparent() throws Exception {
        TraceIdFilter filter = new TraceIdFilter(new PudansLogProperties(),
                tracerProvider(tracerWithCurrentSpan(TRACE_ID, SPAN_ID, false)));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("traceparent", "00-" + OTHER_TRACE_ID + "-" + OTHER_SPAN_ID + "-01");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceInside = new AtomicReference<>();
        AtomicReference<String> spanInside = new AtomicReference<>();
        AtomicReference<String> sampledInside = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) -> {
            traceInside.set(MDC.get(MdcKeys.TRACE_ID));
            spanInside.set(MDC.get(MdcKeys.SPAN_ID));
            sampledInside.set(MDC.get(MdcKeys.SAMPLED));
        };

        filter.doFilter(request, response, chain);

        assertThat(traceInside.get()).isEqualTo(TRACE_ID);
        assertThat(spanInside.get()).isEqualTo(SPAN_ID);
        assertThat(sampledInside.get()).isEqualTo("false");
    }

    private static ObjectProvider<Tracer> tracerProvider(Tracer tracer) {
        return new ObjectProvider<>() {
            @Override
            public Tracer getIfAvailable() {
                return tracer;
            }
        };
    }

    private static Tracer tracerWithCurrentSpan(String traceId, String spanId) {
        return tracerWithCurrentSpan(traceId, spanId, true);
    }

    private static Tracer tracerWithCurrentSpan(String traceId, String spanId, boolean sampled) {
        Tracer tracer = mock(Tracer.class);
        CurrentTraceContext currentContext = mock(CurrentTraceContext.class);
        io.micrometer.tracing.TraceContext context = mock(io.micrometer.tracing.TraceContext.class);
        when(tracer.currentTraceContext()).thenReturn(currentContext);
        when(currentContext.context()).thenReturn(context);
        when(context.traceId()).thenReturn(traceId);
        when(context.spanId()).thenReturn(spanId);
        when(context.sampled()).thenReturn(sampled);
        return tracer;
    }
}