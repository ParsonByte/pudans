package cn.tolim.pudans.security.authentication;

import java.net.URI;
import java.util.Set;
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
 * The web build only ships the auth application. Legacy roots (setup, console,
 * user centre) and the root document are redirected to the single entry page;
 * the auth page itself renders the first-run notice when setup is incomplete.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class InitializeRedirectionWebFilter implements WebFilter {

    private static final String AUTH_ROOT = "/auth/";

    private static final Set<String> LEGACY_ROOTS = Set.of(
            "/login",
            "/setup",
            "/setup/",
            "/setup/index.html",
            "/console",
            "/console/",
            "/uc",
            "/uc/");

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())
                || !LEGACY_ROOTS.contains(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        return redirectStrategy.sendRedirect(exchange, URI.create(AUTH_ROOT));
    }
}
