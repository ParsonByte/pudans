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
 * Serves the bundled auth SPA entry file for /auth and its client-side routes.
 * The web build has a single HTML entry, so every auth route is rewritten to
 * /auth/index.html and direct links survive a reload.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class SpaIndexWebFilter implements WebFilter {

    private static final String AUTH_ROOT = "/auth";
    private static final String AUTH_ROOT_SLASH = "/auth/";
    private static final String AUTH_INDEX = "/auth/index.html";

    private static final Map<String, String> SPA_ROOTS = Map.of(
            AUTH_ROOT, AUTH_INDEX,
            AUTH_ROOT_SLASH, AUTH_INDEX);

    private static final Set<String> INDEX_DOCUMENTS = Set.of(AUTH_INDEX);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().value();
        var indexPath = SPA_ROOTS.get(path);

        if (indexPath == null && isClientRoute(path)) {
            indexPath = AUTH_INDEX;
        }

        if (indexPath == null && !INDEX_DOCUMENTS.contains(path)) {
            return chain.filter(exchange);
        }

        // Auth entry documents carry per-session redirects, so they must never
        // be served from the browser cache.
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
        if (path.endsWith("/index.html") || path.contains(".")) {
            return false;
        }
        return path.startsWith(AUTH_ROOT_SLASH);
    }
}
