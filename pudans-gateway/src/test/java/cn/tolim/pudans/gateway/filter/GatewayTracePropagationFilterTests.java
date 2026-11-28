package cn.tolim.pudans.gateway.filter;

import cn.tolim.pudans.communal.log.PudansLogProperties;
import cn.tolim.pudans.communal.log.TraceWebFilter;
import io.micrometer.tracing.CurrentTraceContext;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GatewayTracePropagationFilterTests {

    @Test
    void injectsTraceparentAndRequestIdIntoDownstreamRequest() {
        Tracer tracer = mock(Tracer.class);
        CurrentTraceContext currentContext = mock(CurrentTraceContext.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentTraceContext()).thenReturn(currentContext);
        when(currentContext.context()).thenReturn(context);
        when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        when(context.spanId()).thenReturn("00f067aa0ba902b7");
        when(context.sampled()).thenReturn(true);
        GatewayTracePropagationFilter filter =
                new GatewayTracePropagationFilter(tracer, new PudansLogProperties());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/orders").build());
        exchange.getAttributes().put(TraceWebFilter.REQUEST_ID_ATTRIBUTE, "req-42");
        AtomicReference<ServerWebExchange> captured = new AtomicReference<>();
        GatewayFilterChain chain = currentExchange -> {
            captured.set(currentExchange);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertThat(captured.get().getRequest().getHeaders().getFirst("traceparent"))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        assertThat(captured.get().getRequest().getHeaders().getFirst("X-Request-Id"))
                .isEqualTo("req-42");
    }
}