package cn.tolim.pudans.security.authentication;

import java.net.URI;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Keeps legacy/root entry points pointed at the auth application:
 * {@code /login}, {@code /recovery}, {@code /legal}, {@code /done} and the
 * trailing-slash {@code /auth/} form are canonicalised to extensionless URLs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class InitializeRedirectionWebFilter implements WebFilter {

    private static final Map<String, String> REDIRECTS = Map.of(
            "/login", "/auth",
            "/recovery", "/auth/recovery",
            "/legal", "/auth/legal",
            "/done", "/auth/signed-in",
            "/signed-in", "/auth/signed-in",
            "/auth/", "/auth");

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())) {
            return chain.filter(exchange);
        }
        var target = REDIRECTS.get(exchange.getRequest().getPath().value());
        if (target == null) {
            return chain.filter(exchange);
        }
        var query = exchange.getRequest().getURI().getRawQuery();
        var location = query == null || query.isBlank() ? target : target + "?" + query;
        return redirectStrategy.sendRedirect(exchange, URI.create(location));
    }
}
