package cn.tolim.pudans.security.authentication;

import cn.tolim.pudans.infra.properties.SecurityProperties;
import java.net.URI;
import java.util.Set;
import lombok.RequiredArgsConstructor;
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

/**
 * Keeps already signed-in users out of the sign-in and recovery pages.
 *
 * <p>The auth SPA is served statically for every visitor, so an authenticated
 * browser could still render the login form by typing {@code /auth/}. This
 * filter runs after the security chain and sends those visitors to the
 * configured post-login destination. The legal documents stay public.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 250)
@RequiredArgsConstructor
public class AuthenticatedAuthPageWebFilter implements WebFilter {

    /** Sign-in entry pages; /auth/legal and /auth/done deliberately stay reachable. */
    private static final Set<String> SIGN_IN_ENTRY_PATHS = Set.of(
            "/auth",
            "/auth/",
            "/auth/index.html",
            "/auth/login",
            "/auth/login/",
            "/auth/recovery",
            "/auth/recovery/");

    private final SecurityProperties securityProperties;

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
                .flatMap(authentication -> redirect(exchange).thenReturn(true))
                .defaultIfEmpty(false)
                .flatMap(redirected -> redirected ? Mono.empty() : chain.filter(exchange));
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

    private Mono<Void> redirect(ServerWebExchange exchange) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(securityProperties.getPostLoginUrl()));
        response.getHeaders().setCacheControl("no-store");
        return response.setComplete();
    }
}
