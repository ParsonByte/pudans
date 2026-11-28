package cn.tolim.pudans.communal.log;

import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.time.Duration;

// Establishes request, trace and span ids for every WebFlux request and logs its completion.
public class TraceWebFilter implements WebFilter, Ordered {

    public static final String TRACEPARENT_HEADER = "traceparent";
    public static final String REQUEST_ID_ATTRIBUTE = "pudans.trace.requestId";
    private static final Logger LOGGER = LoggerFactory.getLogger(TraceWebFilter.class);

    private final PudansLogProperties properties;
    private final ObjectProvider<Tracer> tracerProvider;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public TraceWebFilter(PudansLogProperties properties) {
        this(properties, null);
    }

    public TraceWebFilter(PudansLogProperties properties, ObjectProvider<Tracer> tracerProvider) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
        this.tracerProvider = tracerProvider;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(properties.getTraceHeader());
        if (!TraceContext.isValidRequestId(requestId)) {
            requestId = TraceContext.newRequestId();
        }
        String traceId = null;
        String spanId = null;
        boolean sampled = true;
        Tracer tracer = tracerProvider == null ? null : tracerProvider.getIfAvailable();
        if (tracer != null) {
            io.micrometer.tracing.CurrentTraceContext currentContext = tracer.currentTraceContext();
            io.micrometer.tracing.TraceContext context = currentContext == null
                    ? null : currentContext.context();
            if (context != null) {
                traceId = context.traceId();
                spanId = context.spanId();
                sampled = context.sampled();
            }
        }
        if (traceId == null || traceId.isBlank()) {
            traceId = TraceContext.parseTraceId(exchange.getRequest().getHeaders().getFirst(TRACEPARENT_HEADER))
                    .orElseGet(TraceContext::newTraceId);
        }
        if (spanId == null || spanId.isBlank()) {
            spanId = TraceContext.newSpanId();
        }
        String resolvedRequestId = requestId;
        String resolvedTraceId = traceId;
        String resolvedSpanId = spanId;
        boolean resolvedSampled = sampled;
        exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, resolvedRequestId);
        exchange.getResponse().getHeaders().set(properties.getTraceHeader(), resolvedRequestId);
        HttpMethod method = exchange.getRequest().getMethod();
        String methodName = method.name();
        String path = exchange.getRequest().getPath().value();
        long startedAt = System.nanoTime();
        Mono<Void> result = chain.filter(exchange)
                .contextWrite(context -> context
                        .put(MdcKeys.REQUEST_ID, resolvedRequestId)
                        .put(MdcKeys.TRACE_ID, resolvedTraceId)
                        .put(MdcKeys.SPAN_ID, resolvedSpanId)
                        .put(MdcKeys.SAMPLED, Boolean.toString(resolvedSampled)));
        if (isIgnored(path)) {
            return result;
        }
        return result.doFinally(signal -> logCompletion(methodName, path, exchange.getResponse().getStatusCode(),
                startedAt, resolvedRequestId, resolvedTraceId, resolvedSpanId, resolvedSampled));
    }

    private boolean isIgnored(String path) {
        if (properties.getIgnoredPaths().isEmpty()) {
            return false;
        }
        return properties.getIgnoredPaths().stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    private void logCompletion(String method, String path, HttpStatusCode status, long startedAt,
                               String requestId, String traceId, String spanId, boolean sampled) {
        int statusCode = status == null ? 0 : status.value();
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);
        try (TraceContext.TraceScope scope = TraceContext.open(requestId, traceId, spanId, sampled)) {
            if (elapsed.compareTo(properties.getSlowRequestThreshold()) >= 0) {
                LOGGER.warn("Slow request: method={}, path={}, status={}, elapsedMs={}",
                        method, path, statusCode, elapsed.toMillis());
            } else if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Request completed: method={}, path={}, status={}, elapsedMs={}",
                        method, path, statusCode, elapsed.toMillis());
            }
        }
    }
}