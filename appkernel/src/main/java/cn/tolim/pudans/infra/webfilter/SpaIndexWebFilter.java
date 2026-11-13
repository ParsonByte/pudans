package cn.tolim.pudans.infra.webfilter;

import java.util.Map;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Serves the single SPA entry file for every application root and its
 * client-side routes. The web build has one HTML entry, so direct links such as
 * /console/users or /uc/security survive a reload.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class SpaIndexWebFilter implements WebFilter {

    private static final String INDEX = "/index.html";

    private static final Map<String, String> SPA_ROOTS = Map.of(
            "/auth", INDEX,
            "/auth/", INDEX,
            "/console", INDEX,
            "/console/", INDEX,
            "/uc", INDEX,
            "/uc/", INDEX,
            "/setup", INDEX,
            "/setup/", INDEX,
            "/forbidden", INDEX,
            "/forbidden/", INDEX);

    private static final Set<String> SPA_PREFIXES = Set.of("/auth/", "/console/", "/uc/", "/setup/");

    private static final Set<String> INDEX_DOCUMENTS = Set.of(INDEX);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().value();
        var indexPath = SPA_ROOTS.get(path);

        if (indexPath == null && isClientRoute(path)) {
            indexPath = INDEX;
        }

        if (indexPath == null && !INDEX_DOCUMENTS.contains(path)) {
            return chain.filter(exchange);
        }

        // Entry documents carry per-session redirects, so they must never be
        // served from the browser cache.
        var response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().setCacheControl("no-store");
            return Mono.empty();
        });

        var request =
                indexPath == null ? exchange.getRequest() : exchange.getRequest().mutate().path(indexPath).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static boolean isClientRoute(String path) {
        if (path.endsWith(".html") || path.contains(".")) {
            return false;
        }
        return SPA_PREFIXES.stream().anyMatch(path::startsWith);
    }
}
