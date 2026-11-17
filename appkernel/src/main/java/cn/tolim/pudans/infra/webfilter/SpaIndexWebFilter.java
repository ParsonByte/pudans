package cn.tolim.pudans.infra.webfilter;

import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class SpaIndexWebFilter implements WebFilter {

    private static final String INDEX_ENTRY = "/index.html";

    private static final List<String> RESERVED_PREFIXES = List.of(
            "/apis",
            "/actuator",
            "/assets",
            "/avatars",
            "/challenges",
            "/error",
            "/login",
            "/logout",
            "/webjars");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();

        if (!HttpMethod.GET.equals(request.getMethod())) {
            return chain.filter(exchange);
        }

        var path = request.getPath().value();
        if (path.contains(".") || isReserved(path) || !acceptsHtml(request)) {
            return chain.filter(exchange);
        }

        var response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().setCacheControl("no-store");
            return Mono.empty();
        });

        var rewritten = request.mutate().path(INDEX_ENTRY).build();
        return chain.filter(exchange.mutate().request(rewritten).build());
    }

    private static boolean isReserved(String path) {
        return RESERVED_PREFIXES.stream()
                .anyMatch(prefix -> path.equals(prefix) || path.startsWith(prefix + "/"));
    }

    private static boolean acceptsHtml(ServerHttpRequest request) {
        var accept = request.getHeaders().getAccept();
        return accept.isEmpty()
                || accept.stream().anyMatch(mediaType -> mediaType.isCompatibleWith(MediaType.TEXT_HTML));
    }
}
