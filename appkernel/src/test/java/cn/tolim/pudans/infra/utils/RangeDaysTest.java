package cn.tolim.pudans.infra.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.server.ServerWebInputException;

class RangeDaysTest {

    private static ServerRequest request(String query) {
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/dashboard/overview" + query).build());
        return ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());
    }

    @Test
    void resolveEndDefaultsToNow() {
        var before = Instant.now();
        var end = RangeDays.resolveEnd(request(""));
        var after = Instant.now();

        assertThat(end).isBetween(before, after);
    }

    @Test
    void resolveEndKeepsHistoricalInstant() {
        var expected = Instant.parse("2026-11-20T12:00:00Z");

        assertThat(RangeDays.resolveEnd(request("?to=" + expected))).isEqualTo(expected);
    }

    @Test
    void resolveEndKeepsFutureInstant() {
        var expected = Instant.parse("2026-12-02T15:59:59.999Z");

        assertThat(RangeDays.resolveEnd(request("?to=" + expected))).isEqualTo(expected);
    }

    @Test
    void resolveEndRejectsInvalidInstant() {
        assertThatThrownBy(() -> RangeDays.resolveEnd(request("?to=2026-11-20")))
                .isInstanceOf(ServerWebInputException.class)
                .hasMessageContaining("ISO-8601");
    }
}
