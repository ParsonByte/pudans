package cn.tolim.pudans.gateway.filter;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InternalTokenGatewayFilterTests {

    private static final String SERVICE_PATH = "/apis/pudans.tolim.cn/v1alpha1/mq/messages";
    private static final String CONFIGURED_TOKEN = "configured-internal-token";

    @Test
    void addsConfiguredTokenToForwardedRequest() {
        InternalTokenGatewayFilter filter = new InternalTokenGatewayFilter(CONFIGURED_TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(SERVICE_PATH).build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturingChain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .get(InternalTokenGatewayFilter.TOKEN_HEADER))
                .containsExactly(CONFIGURED_TOKEN);
    }

    @Test
    void replacesClientSuppliedToken() {
        InternalTokenGatewayFilter filter = new InternalTokenGatewayFilter(CONFIGURED_TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(SERVICE_PATH)
                        .header(InternalTokenGatewayFilter.TOKEN_HEADER, "spoofed-token")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturingChain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getHeaders()
                .get(InternalTokenGatewayFilter.TOKEN_HEADER))
                .containsExactly(CONFIGURED_TOKEN);
    }

    @Test
    void keepsPathQueryAndOtherHeaders() {
        InternalTokenGatewayFilter filter = new InternalTokenGatewayFilter(CONFIGURED_TOKEN);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get(SERVICE_PATH + "?page=2")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer token")
                        .build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        filter.filter(exchange, capturingChain(forwarded)).block();

        assertThat(forwarded.get()).isNotNull();
        assertThat(forwarded.get().getRequest().getURI().getPath()).isEqualTo(SERVICE_PATH);
        assertThat(forwarded.get().getRequest().getURI().getQuery()).isEqualTo("page=2");
        assertThat(forwarded.get().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Bearer token");
    }

    @Test
    void runsAfterAuthenticationButBeforeRouting() {
        InternalTokenGatewayFilter filter = new InternalTokenGatewayFilter(CONFIGURED_TOKEN);

        assertThat(filter.getOrder()).isGreaterThan(Ordered.HIGHEST_PRECEDENCE);
        assertThat(filter.getOrder()).isLessThan(0);
    }

    @Test
    void rejectsBlankToken() {
        assertThatThrownBy(() -> new InternalTokenGatewayFilter(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new InternalTokenGatewayFilter("  "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static GatewayFilterChain capturingChain(AtomicReference<ServerWebExchange> forwarded) {
        return exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }
}