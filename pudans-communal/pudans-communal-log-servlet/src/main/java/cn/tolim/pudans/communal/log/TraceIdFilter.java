package cn.tolim.pudans.communal.log;

import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// Establishes request, trace and span ids in the MDC for every servlet request.
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACEPARENT_HEADER = "traceparent";

    private final PudansLogProperties properties;
    private final ObjectProvider<Tracer> tracerProvider;

    public TraceIdFilter(PudansLogProperties properties) {
        this(properties, null);
    }

    public TraceIdFilter(PudansLogProperties properties, ObjectProvider<Tracer> tracerProvider) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
        this.tracerProvider = tracerProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(properties.getTraceHeader());
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
            traceId = TraceContext.parseTraceId(request.getHeader(TRACEPARENT_HEADER))
                    .orElseGet(TraceContext::newTraceId);
        }
        if (spanId == null || spanId.isBlank()) {
            spanId = TraceContext.newSpanId();
        }
        try (TraceContext.TraceScope scope = TraceContext.open(requestId, traceId, spanId, sampled)) {
            response.setHeader(properties.getTraceHeader(), requestId);
            filterChain.doFilter(request, response);
        }
    }
}