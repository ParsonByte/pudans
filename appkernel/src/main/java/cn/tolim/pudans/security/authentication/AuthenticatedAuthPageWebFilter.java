package cn.tolim.pudans.security.authentication;

import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

// Returns no content for signed-in browsers that request the removed auth entry pages.
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 250)
public class AuthenticatedAuthPageWebFilter implements WebFilter {

    private static final Set<String> SIGN_IN_ENTRY_PATHS = Set.of(
            "/auth",
            "/auth/",
            "/auth/index.html",
            "/auth/login",
            "/auth/login/",
            "/auth/recovery",
            "/auth/recovery/");

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        if (!HttpMethod.GET.equals(request.getMethod())
                || !SIGN_IN_ENTRY_PATHS.contains(request.getPath().value())) {
            return chain.filter(exchange);
        }

        return currentAuthentication(exchange)
                .filter(this::isSignedIn)
                .flatMap(authentication -> noContent(exchange).thenReturn(true))
                .defaultIfEmpty(false)
                .flatMap(handled -> handled ? Mono.empty() : chain.filter(exchange));
    }

    private Mono<Authentication> currentAuthentication(ServerWebExchange exchange) {
        return exchange
                .getPrincipal()
                .filter(Authentication.class::isInstance)
                .cast(Authentication.class)
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext()
                        .mapNotNull(SecurityContext::getAuthentication));
    }

    private boolean isSignedIn(Authentication authentication) {
        return trustResolver.isAuthenticated(authentication) && !trustResolver.isAnonymous(authentication);
    }

    private Mono<Void> noContent(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.NO_CONTENT);
        response.getHeaders().setCacheControl("no-store");
        return response.setComplete();
    }
}
