package cn.tolim.pudans.communal.log;

import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TraceWebFilterTests {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";
    private static final String OTHER_TRACE_ID = "0af7651916cd43dd8448eb211c80319c";
    private static final String OTHER_SPAN_ID = "b7ad6b7169203331";

    @Test
    void echoesRequestIdAndCompletesNormally() {
        TraceWebFilter filter = new TraceWebFilter(new PudansLogProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").header("X-Request-Id", "req-42").build());
        AtomicBoolean invoked = new AtomicBoolean();
        WebFilterChain chain = currentExchange -> {
            invoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo("req-42");
        assertThat(invoked).isTrue();
    }

    @Test
    void generatesRequestIdWhenIncomingValueIsInvalid() {
        TraceWebFilter filter = new TraceWebFilter(new PudansLogProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").header("X-Request-Id", "bad\nvalue").build());

        filter.filter(exchange, currentExchange -> Mono.empty()).block(Duration.ofSeconds(5));

        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).matches("[0-9a-f]{32}");
    }

    @Test
    void reusesIncomingTraceparentWhenNoSpanIsActive() {
        TraceWebFilter filter = new TraceWebFilter(new PudansLogProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header("traceparent", "00-" + TRACE_ID + "-" + SPAN_ID + "-01")
                        .build());
        AtomicReference<String> traceId = new AtomicReference<>();
        WebFilterChain chain = currentExchange -> Mono.deferContextual(context -> {
            traceId.set(context.get(MdcKeys.TRACE_ID));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(traceId.get()).isEqualTo(TRACE_ID);
    }

    @Test
    void publishesCurrentTracerSpanIdsToTheReactorContext() {
        TraceWebFilter filter = new TraceWebFilter(new PudansLogProperties(),
                tracerProvider(tracerWithCurrentSpan(TRACE_ID, SPAN_ID, true)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders")
                        .header("traceparent", "00-" + OTHER_TRACE_ID + "-" + OTHER_SPAN_ID + "-01")
                        .build());
        AtomicReference<String> traceId = new AtomicReference<>();
        AtomicReference<String> spanId = new AtomicReference<>();
        AtomicReference<String> sampled = new AtomicReference<>();
        WebFilterChain chain = currentExchange -> Mono.deferContextual(context -> {
            traceId.set(context.get(MdcKeys.TRACE_ID));
            spanId.set(context.get(MdcKeys.SPAN_ID));
            sampled.set(context.get(MdcKeys.SAMPLED));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(traceId.get()).isEqualTo(TRACE_ID);
        assertThat(spanId.get()).isEqualTo(SPAN_ID);
        assertThat(sampled.get()).isEqualTo("true");
    }

    @Test
    void publishesUnsampledContextToTheReactorContext() {
        TraceWebFilter filter = new TraceWebFilter(new PudansLogProperties(),
                tracerProvider(tracerWithCurrentSpan(TRACE_ID, SPAN_ID, false)));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());
        AtomicReference<String> sampled = new AtomicReference<>();
        WebFilterChain chain = currentExchange -> Mono.deferContextual(context -> {
            sampled.set(context.get(MdcKeys.SAMPLED));
            return Mono.empty();
        });

        filter.filter(exchange, chain).block(Duration.ofSeconds(5));

        assertThat(sampled.get()).isEqualTo("false");
    }

    @Test
    void exposesRequestIdAsExchangeAttribute() {
        TraceWebFilter filter = new TraceWebFilter(new PudansLogProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").header("X-Request-Id", "req-42").build());

        filter.filter(exchange, currentExchange -> Mono.empty()).block(Duration.ofSeconds(5));

        assertThat((String) exchange.getAttribute(TraceWebFilter.REQUEST_ID_ATTRIBUTE)).isEqualTo("req-42");
    }

    @Test
    void completesForIgnoredPathsAndKeepsResponseHeader() {
        TraceWebFilter filter = new TraceWebFilter(new PudansLogProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/actuator/health").header("X-Request-Id", "req-42").build());
        AtomicBoolean invoked = new AtomicBoolean();

        filter.filter(exchange, currentExchange -> {
            invoked.set(true);
            currentExchange.getResponse().setStatusCode(HttpStatus.OK);
            return Mono.empty();
        }).block(Duration.ofSeconds(5));

        assertThat(invoked).isTrue();
        assertThat(exchange.getResponse().getHeaders().getFirst("X-Request-Id")).isEqualTo("req-42");
    }

    private static ObjectProvider<Tracer> tracerProvider(Tracer tracer) {
        return new ObjectProvider<>() {
            @Override
            public Tracer getIfAvailable() {
                return tracer;
            }
        };
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