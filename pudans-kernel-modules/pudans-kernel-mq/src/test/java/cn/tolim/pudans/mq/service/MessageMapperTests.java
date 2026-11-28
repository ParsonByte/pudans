package cn.tolim.pudans.mq.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MessageMapperTests {

    private final MessageMapper mapper = new MessageMapper();

    @Test
    void roundTripsPayloadJson() {
        String json = mapper.serializePayload(Map.of("orderId", 42, "items", List.of("a", "b")));

        Object parsed = mapper.parsePayload(json);

        assertThat(parsed).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) parsed).get("orderId")).isEqualTo(42);
    }

    @Test
    void roundTripsHeaders() {
        String json = mapper.serializeHeaders(Map.of("traceId", "t-1"));

        assertThat(mapper.parseHeaders(json)).containsEntry("traceId", "t-1");
    }

    @Test
    void treatsMissingHeadersAsEmpty() {
        assertThat(mapper.parseHeaders(null)).isEmpty();
        assertThat(mapper.parseHeaders(" ")).isEmpty();
        assertThat(mapper.serializeHeaders(null)).isNull();
        assertThat(mapper.serializeHeaders(Map.of())).isNull();
    }
}