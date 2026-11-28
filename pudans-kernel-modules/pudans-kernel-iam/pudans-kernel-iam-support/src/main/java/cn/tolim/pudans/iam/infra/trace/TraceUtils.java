package cn.tolim.pudans.iam.infra.trace;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;

public final class TraceUtils {
    public static final String REQUEST_ID = "requestId";
    private static final String TRACE_ID = "TRACE_ID";

    private TraceUtils() {
    }

    public static void put(String value) {
        MDC.put(TRACE_ID, value);
    }

    public static void remove() {
        MDC.remove(TRACE_ID);
    }

    public static String get() {
        return MDC.get(TRACE_ID);
    }

    public static String getRequestId(HttpServletRequest request) {
        for (String name : new String[]{"X-Request-Id", "X-Correlation-Id", REQUEST_ID,
                "traceId"}) {
            String value = request.getParameter(name);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        String header = request.getHeader("X-Request-Id");
        return header != null && !header.isBlank() ? header : null;
    }
}
