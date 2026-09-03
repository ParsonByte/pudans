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
 * Serves the bundled SPA entry files for application roots and client-side
 * routes. Spring WebFlux only treats the top-level static/index.html as a
 * welcome page, so nested apps such as /setup/ and /console/ need an explicit
 * path rewrite. Routes such as /auth/recovery are rewritten to
 * /auth/index.html so direct links survive a reload.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class SpaIndexWebFilter implements WebFilter {

    private static final Map<String, String> SPA_ROOTS = Map.of(
            "/setup", "/setup/index.html",
            "/setup/", "/setup/index.html",
            "/auth", "/auth/index.html",
            "/auth/", "/auth/index.html",
            "/console", "/console/index.html",
            "/console/", "/console/index.html",
            "/uc", "/uc/index.html",
            "/uc/", "/uc/index.html");

    private static final Map<String, String> SPA_INDEXES = Map.of(
            "/setup", "/setup/index.html",
            "/auth", "/auth/index.html",
            "/console", "/console/index.html",
            "/uc", "/uc/index.html");

    private static final Set<String> INDEX_DOCUMENTS =
            Set.of("/setup/index.html", "/auth/index.html", "/console/index.html", "/uc/index.html");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().value();
        var indexPath = SPA_ROOTS.get(path);

        if (indexPath == null && isClientRoute(path)) {
            indexPath = SPA_INDEXES.entrySet().stream()
                    .filter(entry -> path.startsWith(entry.getKey() + "/"))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(null);
        }

        if (indexPath == null && !INDEX_DOCUMENTS.contains(path)) {
            return chain.filter(exchange);
        }

        // SPA entry documents carry per-session redirects (sign-in guards, setup
        // redirects), so they must never be served from the browser cache.
        var response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().setCacheControl("no-store");
            return Mono.empty();
        });

        var request = indexPath == null ? exchange.getRequest() : exchange.getRequest().mutate().path(indexPath).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static boolean isClientRoute(String path) {
        if (path.endsWith("/index.html") || path.contains(".")) {
            return false;
        }
        return SPA_INDEXES.keySet().stream()
                .anyMatch(root -> path.startsWith(root + "/"));
    }
}
