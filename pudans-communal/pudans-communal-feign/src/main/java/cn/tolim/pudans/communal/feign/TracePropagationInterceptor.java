package cn.tolim.pudans.communal.feign;

import cn.tolim.pudans.communal.feign.configuration.PudansFeignProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.MDC;
import org.springframework.core.Ordered;

import java.util.Locale;

// Propagates request, trace and service identity headers to downstream services.
public class TracePropagationInterceptor implements RequestInterceptor, Ordered {

    private static final String TRACEPARENT_VERSION = "00";

    private final PudansFeignProperties properties;

    public TracePropagationInterceptor(PudansFeignProperties properties) {
        if (properties == null) {
            throw new IllegalArgumentException("properties must not be null");
        }
        this.properties = properties;
    }

    @Override
    public void apply(RequestTemplate template) {
        if (properties.isPropagateRequestId()) {
            String requestId = MDC.get(PudansHeaders.MDC_REQUEST_ID);
            if (requestId == null || requestId.isBlank()) {
                requestId = MDC.get(PudansHeaders.MDC_TRACE_ID);
            }
            if (requestId != null && !requestId.isBlank() && !hasHeader(template, PudansHeaders.REQUEST_ID)) {
                template.header(PudansHeaders.REQUEST_ID, requestId);
            }
        }
        if (properties.isPropagateTraceparent() && !hasHeader(template, PudansHeaders.TRACEPARENT)) {
            String traceId = MDC.get(PudansHeaders.MDC_TRACE_ID);
            String spanId = MDC.get(PudansHeaders.MDC_SPAN_ID);
            if (isHex(traceId, 32) && isHex(spanId, 16)) {
                String sampled = MDC.get(PudansHeaders.MDC_SAMPLED);
                String flags = sampled == null || Boolean.parseBoolean(sampled) ? "01" : "00";
                template.header(PudansHeaders.TRACEPARENT, TRACEPARENT_VERSION + "-"
                        + traceId.toLowerCase(Locale.ROOT) + "-"
                        + spanId.toLowerCase(Locale.ROOT) + "-" + flags);
            }
        }
        String serviceName = properties.getServiceName();
        if (serviceName != null && !serviceName.isBlank() && !hasHeader(template, PudansHeaders.SERVICE_NAME)) {
            template.header(PudansHeaders.SERVICE_NAME, serviceName);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }

    private boolean hasHeader(RequestTemplate template, String name) {
        return template.headers().keySet().stream().anyMatch(headerName -> headerName.equalsIgnoreCase(name));
    }

    private boolean isHex(String value, int expectedLength) {
        if (value == null || value.length() != expectedLength) {
            return false;
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean hex = (character >= '0' && character <= '9')
                    || (character >= 'a' && character <= 'f')
                    || (character >= 'A' && character <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }
}