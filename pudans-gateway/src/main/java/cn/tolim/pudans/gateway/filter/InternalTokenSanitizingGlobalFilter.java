package cn.tolim.pudans.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

// Removes a client supplied internal token so only the gateway can assert the internal identity
@Component
public class InternalTokenSanitizingGlobalFilter implements GlobalFilter, Ordered {

    // Runs after authentication and before service route filters inject the trusted token
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 1;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        if (!request.getHeaders().containsHeader(InternalTokenGatewayFilter.TOKEN_HEADER)) {
            return chain.filter(exchange);
        }
        ServerHttpRequest sanitized = request.mutate()
                .headers(headers -> headers.remove(InternalTokenGatewayFilter.TOKEN_HEADER))
                .build();
        return chain.filter(exchange.mutate().request(sanitized).build());
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}