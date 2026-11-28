package cn.tolim.pudans.gateway.filter;

import cn.tolim.pudans.communal.log.PudansLogProperties;
import cn.tolim.pudans.communal.log.TraceWebFilter;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Locale;

@Component
public class GatewayTracePropagationFilter implements GlobalFilter, Ordered {

    private final Tracer tracer;
    private final PudansLogProperties properties;

    public GatewayTracePropagationFilter(Tracer tracer, PudansLogProperties properties) {
        this.tracer = tracer;
        this.properties = properties;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest.Builder requestBuilder = exchange.getRequest().mutate();
        TraceContext context = tracer.currentTraceContext().context();
        if (context != null) {
            String flags = context.sampled() ? "01" : "00";
            requestBuilder.header("traceparent", "00-"
                    + context.traceId().toLowerCase(Locale.ROOT) + "-"
                    + context.spanId().toLowerCase(Locale.ROOT) + "-" + flags);
        }
        Object requestId = exchange.getAttribute(TraceWebFilter.REQUEST_ID_ATTRIBUTE);
        if (requestId != null) {
            requestBuilder.header(properties.getTraceHeader(), requestId.toString());
        }
        return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 30;
    }
}