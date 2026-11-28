package cn.tolim.pudans.communal.feign;

public final class PudansHeaders {

    public static final String REQUEST_ID = "X-Request-Id";
    public static final String SERVICE_NAME = "X-Pudans-Service";
    public static final String TRACEPARENT = "traceparent";

    public static final String MDC_TRACE_ID = "traceId";
    public static final String MDC_SPAN_ID = "spanId";
    public static final String MDC_SAMPLED = "sampled";
    public static final String MDC_REQUEST_ID = "requestId";

    private PudansHeaders() {
    }
}