package cn.tolim.pudans.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InternalTokenSanitizingGlobalFilterTests {

    private static final String SERVICE_PATH = "/apis/pudans.tolim.cn/v1alpha1/mq/messages";

    private final InternalTokenSanitizingGlobalFilter filter =
            new InternalTokenSanitizingGlobalFilter();

    @Test
    void removesClientSuppliedToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(SERVICE_PATH)
                        .header(InternalTokenGatewayFilter.TOKEN_HEADER, "spoofed-token")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturingChain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .containsHeader(InternalTokenGatewayFilter.TOKEN_HEADER)).isFalse();
    }

    @Test
    void leavesRequestsWithoutTokenUntouched() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(SERVICE_PATH).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturingChain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .containsHeader(InternalTokenGatewayFilter.TOKEN_HEADER)).isFalse();
        assertThat(forwarded.get().getRequest().getURI().getPath()).isEqualTo(SERVICE_PATH);
    }

    @Test
    void runsAfterAuthentication() {
        assertThat(filter.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
        assertThat(filter.getOrder()).isLessThan(0);
    }

    private static GatewayFilterChain capturingChain(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }
}