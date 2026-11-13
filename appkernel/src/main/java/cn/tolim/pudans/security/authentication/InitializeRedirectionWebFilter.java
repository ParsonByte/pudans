package cn.tolim.pudans.security.authentication;

import java.net.URI;
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

/** Keeps the legacy /login entry pointed at the single-page auth application. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
class InitializeRedirectionWebFilter implements WebFilter {

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (!HttpMethod.GET.equals(exchange.getRequest().getMethod())
                || !"/login".equals(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }
        return redirectStrategy.sendRedirect(exchange, URI.create("/auth/"));
    }
}
