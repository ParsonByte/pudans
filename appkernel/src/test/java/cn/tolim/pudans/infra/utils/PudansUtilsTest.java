package cn.tolim.pudans.infra.utils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.function.server.ServerRequest;

class PudansUtilsTest {

    @Test
    void noCacheAddsResponseHeaders() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/login").build());
        var request = ServerRequest.create(exchange, HandlerStrategies.withDefaults().messageReaders());

        PudansUtils.noCache().apply(request);

        var headers = exchange.getResponse().getHeaders();
        assertThat(headers.getCacheControl()).contains("no-store");
        assertThat(headers.getFirst(HttpHeaders.PRAGMA)).isEqualTo("no-cache");
    }
}
