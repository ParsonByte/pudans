package cn.tolim.pudans.communal.log;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TraceContextTests {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void parsesValidTraceparent() {
        assertThat(TraceContext.parseTraceId("00-" + TRACE_ID + "-" + SPAN_ID + "-01"))
                .contains(TRACE_ID);
    }

    @Test
    void rejectsMalformedAndZeroTraceparents() {
        assertThat(TraceContext.parseTraceId("garbage")).isEmpty();
        assertThat(TraceContext.parseTraceId("00-00000000000000000000000000000000-" + SPAN_ID + "-01")).isEmpty();
        assertThat(TraceContext.parseTraceId("00-" + TRACE_ID + "-0000000000000000-01")).isEmpty();
    }

    @Test
    void openPublishesIdsAndRestoresPreviousState() {
        MDC.put(MdcKeys.TRACE_ID, "previous");

        try (TraceContext.TraceScope scope = TraceContext.open("req-1", TRACE_ID, SPAN_ID)) {
            assertThat(TraceContext.traceId()).contains(TRACE_ID);
            assertThat(TraceContext.spanId()).contains(SPAN_ID);
            assertThat(TraceContext.requestId()).contains("req-1");
            assertThat(TraceContext.traceparent()).contains("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
        }

        assertThat(TraceContext.traceId()).contains("previous");
        assertThat(TraceContext.requestId()).isEmpty();
    }

    @Test
    void openPublishesSampledFlag() {
        try (TraceContext.TraceScope scope = TraceContext.open("req-1", TRACE_ID, SPAN_ID, false)) {
            assertThat(MDC.get(MdcKeys.SAMPLED)).isEqualTo("false");
            assertThat(TraceContext.traceparent()).contains("00-" + TRACE_ID + "-" + SPAN_ID + "-00");
        }
    }

    @Test
    void closingTwiceIsIdempotent() {
        TraceContext.TraceScope scope = TraceContext.open("req-1", TRACE_ID, SPAN_ID);

        scope.close();
        scope.close();

        assertThat(TraceContext.traceId()).isEmpty();
    }

    @Test
    void generatesHexIdentifiers() {
        assertThat(TraceContext.newTraceId()).hasSize(32).matches("[0-9a-f]{32}");
        assertThat(TraceContext.newSpanId()).hasSize(16).matches("[0-9a-f]{16}");
        assertThat(TraceContext.newRequestId()).hasSize(32);
    }

    @Test
    void validatesRequestIds() {
        assertThat(TraceContext.isValidRequestId("req-42")).isTrue();
        assertThat(TraceContext.isValidRequestId(null)).isFalse();
        assertThat(TraceContext.isValidRequestId(" ")).isFalse();
        assertThat(TraceContext.isValidRequestId("bad\nvalue")).isFalse();
        assertThat(TraceContext.isValidRequestId("x".repeat(129))).isFalse();
    }
}