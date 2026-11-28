package cn.tolim.pudans.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// Injects the configured internal token into requests routed to internal services
public class InternalTokenGatewayFilter implements GatewayFilter, Ordered {

    // Header shared by the gateway and every internal service
    public static final String TOKEN_HEADER = "X-Pudans-Internal-Token";

    // Runs after authentication and before the routing filter
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    private final String token;

    public InternalTokenGatewayFilter(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Internal token must not be blank");
        }
        this.token = token;
    }

    public String getToken() {
        return token;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(TOKEN_HEADER, token))
                .build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}