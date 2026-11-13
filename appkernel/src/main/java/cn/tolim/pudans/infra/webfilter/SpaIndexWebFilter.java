package cn.tolim.pudans.infra.webfilter;

import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Serves the correct multi-page HTML entry for every application root and its
 * client-side routes, mirroring Halo's production mapping:
 *
 * <ul>
 *   <li>{@code /console/**} to {@code static/console.html}</li>
 *   <li>{@code /uc/**} to {@code static/uc.html}</li>
 *   <li>{@code /auth/**}, {@code /setup}, {@code /forbidden} to {@code static/auth.html}</li>
 * </ul>
 *
 * <p>There is deliberately no environment-based base path: console and UC are
 * fixed mounts, exactly like the Vite {@code createWebHistory} bases.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 200)
public class SpaIndexWebFilter implements WebFilter {

    private static final String AUTH_ENTRY = "/auth.html";
    private static final String CONSOLE_ENTRY = "/console.html";
    private static final String UC_ENTRY = "/uc.html";

    private static final Map<String, String> EXACT_ENTRIES = Map.ofEntries(
            Map.entry("/", AUTH_ENTRY),
            Map.entry("/auth", AUTH_ENTRY),
            Map.entry("/auth/", AUTH_ENTRY),
            Map.entry("/setup", AUTH_ENTRY),
            Map.entry("/setup/", AUTH_ENTRY),
            Map.entry("/forbidden", AUTH_ENTRY),
            Map.entry("/forbidden/", AUTH_ENTRY),
            Map.entry("/console", CONSOLE_ENTRY),
            Map.entry("/console/", CONSOLE_ENTRY),
            Map.entry("/uc", UC_ENTRY),
            Map.entry("/uc/", UC_ENTRY));

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var path = exchange.getRequest().getPath().value();

        if (path.endsWith(".html") || path.contains(".")) {
            return chain.filter(exchange);
        }

        var entry = EXACT_ENTRIES.get(path);
        if (entry == null) {
            entry = prefixEntry(path);
        }
        if (entry == null) {
            return chain.filter(exchange);
        }

        // Entry documents carry per-session redirects, so they must never be
        // served from the browser cache.
        var response = exchange.getResponse();
        response.beforeCommit(() -> {
            response.getHeaders().setCacheControl("no-store");
            return Mono.empty();
        });

        var request = exchange.getRequest().mutate().path(entry).build();
        return chain.filter(exchange.mutate().request(request).build());
    }

    private static String prefixEntry(String path) {
        if (path.startsWith("/console/")) {
            return CONSOLE_ENTRY;
        }
        if (path.startsWith("/uc/")) {
            return UC_ENTRY;
        }
        if (path.startsWith("/auth/") || path.startsWith("/setup/") || path.startsWith("/forbidden/")) {
            return AUTH_ENTRY;
        }
        return null;
    }
}
