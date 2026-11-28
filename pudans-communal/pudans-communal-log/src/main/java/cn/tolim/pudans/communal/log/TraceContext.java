package cn.tolim.pudans.communal.log;

import org.slf4j.MDC;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// MDC based trace context following W3C traceparent naming.
public final class TraceContext {

    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-([0-9a-f]{16})-[0-9a-f]{2}$", Pattern.CASE_INSENSITIVE);
    private static final String ZERO_TRACE_ID = "00000000000000000000000000000000";
    private static final String ZERO_SPAN_ID = "0000000000000000";
    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private TraceContext() {
    }

    public static Optional<String> traceId() {
        return Optional.ofNullable(MDC.get(MdcKeys.TRACE_ID));
    }

    public static Optional<String> spanId() {
        return Optional.ofNullable(MDC.get(MdcKeys.SPAN_ID));
    }

    public static Optional<String> requestId() {
        return Optional.ofNullable(MDC.get(MdcKeys.REQUEST_ID));
    }

    public static Optional<String> userId() {
        return Optional.ofNullable(MDC.get(MdcKeys.USER_ID));
    }

    public static Optional<String> organizationId() {
        return Optional.ofNullable(MDC.get(MdcKeys.ORGANIZATION_ID));
    }

    public static Optional<String> appCode() {
        return Optional.ofNullable(MDC.get(MdcKeys.APP_CODE));
    }

    // Builds a W3C traceparent header when the current ids are valid.
    public static Optional<String> traceparent() {
        String traceId = MDC.get(MdcKeys.TRACE_ID);
        String spanId = MDC.get(MdcKeys.SPAN_ID);
        if (!isHex(traceId, 32) || !isHex(spanId, 16)) {
            return Optional.empty();
        }
        // W3C traceparent requires lowercase hex.
        String sampled = MDC.get(MdcKeys.SAMPLED);
        String flags = sampled == null || Boolean.parseBoolean(sampled) ? "01" : "00";
        return Optional.of("00-" + traceId.toLowerCase(Locale.ROOT)
                + "-" + spanId.toLowerCase(Locale.ROOT) + "-" + flags);
    }

    public static Optional<String> parseTraceId(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = TRACEPARENT_PATTERN.matcher(traceparent.trim());
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String traceId = matcher.group(1).toLowerCase();
        String parentSpanId = matcher.group(2).toLowerCase();
        if (ZERO_TRACE_ID.equals(traceId) || ZERO_SPAN_ID.equals(parentSpanId)) {
            return Optional.empty();
        }
        return Optional.of(traceId);
    }

    public static String newTraceId() {
        return randomHex(32);
    }

    public static String newSpanId() {
        return randomHex(16);
    }

    public static String newRequestId() {
        return randomHex(32);
    }

    // Rejects control characters and oversized values before they reach response headers and logs.
    public static boolean isValidRequestId(String requestId) {
        if (requestId == null || requestId.isBlank() || requestId.length() > MAX_REQUEST_ID_LENGTH) {
            return false;
        }
        for (int index = 0; index < requestId.length(); index++) {
            char character = requestId.charAt(index);
            if (character < 0x21 || character > 0x7e) {
                return false;
            }
        }
        return true;
    }

    // Opens a trace scope; closing restores the previous MDC state.
    public static TraceScope open(String requestId, String traceId, String spanId) {
        return open(requestId, traceId, spanId, true);
    }

    public static TraceScope open(String requestId, String traceId, String spanId, boolean sampled) {
        if (requestId == null || requestId.isBlank()) {
            throw new IllegalArgumentException("requestId must not be blank");
        }
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("traceId must not be blank");
        }
        if (spanId == null || spanId.isBlank()) {
            throw new IllegalArgumentException("spanId must not be blank");
        }
        Map<String, String> previous = MDC.getCopyOfContextMap();
        MDC.put(MdcKeys.REQUEST_ID, requestId);
        MDC.put(MdcKeys.TRACE_ID, traceId);
        MDC.put(MdcKeys.SPAN_ID, spanId);
        MDC.put(MdcKeys.SAMPLED, Boolean.toString(sampled));
        return new TraceScope(previous);
    }

    public static void putUserId(String userId) {
        putOrRemove(MdcKeys.USER_ID, userId);
    }

    public static void putOrganizationId(String organizationId) {
        putOrRemove(MdcKeys.ORGANIZATION_ID, organizationId);
    }

    public static void putAppCode(String appCode) {
        putOrRemove(MdcKeys.APP_CODE, appCode);
    }

    public static void clear() {
        MDC.clear();
    }

    private static void putOrRemove(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
        } else {
            MDC.put(key, value);
        }
    }

    private static boolean isHex(String value, int length) {
        if (value == null || value.length() != length) {
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

    private static String randomHex(int length) {
        java.security.SecureRandom random = RANDOM.get();
        char[] value = new char[length];
        for (int index = 0; index < length; index++) {
            value[index] = HEX[random.nextInt(HEX.length)];
        }
        return new String(value);
    }

    private static final ThreadLocal<java.security.SecureRandom> RANDOM =
            ThreadLocal.withInitial(java.security.SecureRandom::new);

    public static final class TraceScope implements AutoCloseable {

        private final Map<String, String> previous;
        private boolean closed;

        private TraceScope(Map<String, String> previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (previous == null) {
                MDC.clear();
            } else {
                MDC.setContextMap(previous);
            }
        }
    }
}