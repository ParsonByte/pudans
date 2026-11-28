package cn.tolim.pudans.communal.feign;

import cn.tolim.pudans.communal.feign.configuration.PudansFeignProperties;
import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

class TracePropagationInterceptorTests {

    private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";
    private static final String SPAN_ID = "00f067aa0ba902b7";

    private final PudansFeignProperties properties = new PudansFeignProperties();
    private final TracePropagationInterceptor interceptor = new TracePropagationInterceptor(properties);

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void propagatesRequestIdAndTraceparent() {
        MDC.put(PudansHeaders.MDC_REQUEST_ID, "req-1");
        MDC.put(PudansHeaders.MDC_TRACE_ID, TRACE_ID);
        MDC.put(PudansHeaders.MDC_SPAN_ID, SPAN_ID);
        RequestTemplate template = template();

        interceptor.apply(template);

        assertThat(template.headers().get(PudansHeaders.REQUEST_ID)).containsExactly("req-1");
        assertThat(template.headers().get(PudansHeaders.TRACEPARENT))
                .containsExactly("00-" + TRACE_ID + "-" + SPAN_ID + "-01");
    }

    @Test
    void propagatesUnsampledFlagWhenContextIsNotSampled() {
        MDC.put(PudansHeaders.MDC_REQUEST_ID, "req-1");
        MDC.put(PudansHeaders.MDC_TRACE_ID, TRACE_ID);
        MDC.put(PudansHeaders.MDC_SPAN_ID, SPAN_ID);
        MDC.put(PudansHeaders.MDC_SAMPLED, "false");
        RequestTemplate template = template();

        interceptor.apply(template);

        assertThat(template.headers().get(PudansHeaders.TRACEPARENT))
                .containsExactly("00-" + TRACE_ID + "-" + SPAN_ID + "-00");
    }

    @Test
    void doesNotDuplicateExistingHeaders() {
        MDC.put(PudansHeaders.MDC_REQUEST_ID, "req-1");
        RequestTemplate template = template();
        template.header(PudansHeaders.REQUEST_ID, "existing");

        interceptor.apply(template);

        assertThat(template.headers().get(PudansHeaders.REQUEST_ID)).containsExactly("existing");
    }

    @Test
    void propagatesConfiguredServiceName() {
        properties.setServiceName("iam");
        RequestTemplate template = template();

        interceptor.apply(template);

        assertThat(template.headers().get(PudansHeaders.SERVICE_NAME)).containsExactly("iam");
    }

    @Test
    void skipsInvalidTraceIds() {
        MDC.put(PudansHeaders.MDC_TRACE_ID, "short");
        MDC.put(PudansHeaders.MDC_SPAN_ID, SPAN_ID);
        RequestTemplate template = template();

        interceptor.apply(template);

        assertThat(template.headers()).doesNotContainKey(PudansHeaders.TRACEPARENT);
    }

    private RequestTemplate template() {
        return new RequestTemplate().method("GET").target("http://localhost/api");
    }
}