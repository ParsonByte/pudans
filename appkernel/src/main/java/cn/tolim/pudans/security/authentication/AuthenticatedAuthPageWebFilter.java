package cn.tolim.pudans.security.authentication;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import cn.tolim.pudans.core.rbac.service.UiPermissionService;
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
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

/**
 * Keeps already signed-in users out of the sign-in application.
 *
 * <p>The auth SPA is served statically for every visitor, so an authenticated
 * browser could still render the login form by typing {@code /auth/}. This
 * filter runs after the security chain (so the session principal is available)
 * and before {@code SpaIndexWebFilter} rewrites the path to index.html.
 *
 * <p>The destination is resolved in this order:
 * <ol>
 *   <li>the {@code Referer} page, when the visitor came from {@code /console}
 *       or {@code /uc};</li>
 *   <li>the saved request, when the visitor was bounced here from a protected
 *       page before signing in;</li>
 *   <li>{@code /console/} for accounts holding console ui permissions;</li>
 *   <li>{@code /uc/} for everybody else, which is the default home of a
 *       signed-in account.</li>
 * </ol>
 * Public legal documents stay reachable for signed-in users.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE - 250)
@RequiredArgsConstructor
public class AuthenticatedAuthPageWebFilter implements WebFilter {

    public static final String CONSOLE_LOCATION = "/console/";
    public static final String USER_CENTER_LOCATION = "/uc/";

    private static final String ALL_UI_PERMISSIONS = "*";

    private static final String CONSOLE_REFERER_PREFIX = "/console";

    private static final String USER_CENTER_REFERER_PREFIX = "/uc";

    /** Console ui permission tags that prove an account may open the console. */
    private static final Set<String> CONSOLE_UI_PERMISSIONS = Set.of(
            "dashboard:view",
            "users:manage",
            "roles:manage",
            "audit:view",
            "risk:manage",
            "webhooks:manage",
            "settings:manage",
            "auth-providers:manage",
            "plugins:manage",
            "ops:view");

    /** Sign-in entry pages; /auth/legal deliberately stays public. */
    private static final Set<String> SIGN_IN_ENTRY_PATHS = Set.of(
            "/auth",
            "/auth/",
            "/auth/index.html",
            "/auth/login",
            "/auth/login/",
            "/auth/recovery",
            "/auth/recovery/");

    private final AuthenticationTrustResolver trustResolver = new AuthenticationTrustResolverImpl();

    private final ServerRequestCache serverRequestCache;

    private final UiPermissionService uiPermissionService;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var request = exchange.getRequest();
        if (!HttpMethod.GET.equals(request.getMethod())
                || !SIGN_IN_ENTRY_PATHS.contains(request.getPath().value())) {
            return chain.filter(exchange);
        }

        return currentAuthentication(exchange)
                .filter(this::isSignedIn)
                .flatMap(authentication -> resolveDestination(exchange, authentication))
                .map(Optional::of)
                .defaultIfEmpty(Optional.empty())
                .flatMap(destination -> destination
                        .map(location -> redirect(exchange, location))
                        .orElseGet(() -> chain.filter(exchange)));
    }

    private Mono<Authentication> currentAuthentication(ServerWebExchange exchange) {
        return exchange.getPrincipal()
                .filter(Authentication.class::isInstance)
                .cast(Authentication.class)
                .switchIfEmpty(ReactiveSecurityContextHolder.getContext()
                        .mapNotNull(SecurityContext::getAuthentication));
    }

    private boolean isSignedIn(Authentication authentication) {
        return trustResolver.isAuthenticated(authentication) && !trustResolver.isAnonymous(authentication);
    }

    /**
     * The page the visitor came from wins, then the saved request, then the
     * ui-permission based default.
     */
    private Mono<String> resolveDestination(ServerWebExchange exchange, Authentication authentication) {
        return uiPermissionService
                .uiPermissionsOf(authentication.getName())
                .map(AuthenticatedAuthPageWebFilter::canAccessConsole)
                .flatMap(canAccessConsole -> {
                    var refererLocation = refererLocation(exchange.getRequest(), canAccessConsole);
                    if (refererLocation != null) {
                        return Mono.just(refererLocation);
                    }
                    return serverRequestCache
                            .getRedirectUri(exchange)
                            .flatMap(uri -> Mono.justOrEmpty(savedRequestLocation(exchange, uri, canAccessConsole)))
                            .switchIfEmpty(Mono.just(canAccessConsole ? CONSOLE_LOCATION : USER_CENTER_LOCATION));
                });
    }

    /**
     * Only same-application page requests are replayed: API calls, the sign-in
     * app itself and the root document would either loop or land on nothing.
     */
    private String savedRequestLocation(ServerWebExchange exchange, URI uri, boolean canAccessConsole) {
        var location = PudansServerRequestCache.uriInApplication(exchange.getRequest(), uri);
        if (location.startsWith("/apis/")
                || location.startsWith("/auth")
                || location.startsWith("/login")
                || location.startsWith("/logout")
                || location.equals("/")
                || location.equals("/index.html")) {
            return null;
        }
        if (!canAccessConsole && location.startsWith("/console")) {
            return null;
        }
        return location;
    }

    /**
     * A visitor arriving from the console or the user centre is sent back to
     * the application the request came from, as long as the account may open
     * it. Anything else falls through to the saved request and the default.
     */
    private String refererLocation(
            org.springframework.http.server.reactive.ServerHttpRequest request, boolean canAccessConsole) {
        var referer = request.getHeaders().getFirst(org.springframework.http.HttpHeaders.REFERER);
        if (!StringUtils.hasText(referer)) {
            return null;
        }
        try {
            var path = URI.create(referer).getPath();
            if (path.startsWith(CONSOLE_REFERER_PREFIX)) {
                return canAccessConsole ? CONSOLE_LOCATION : null;
            }
            if (path.startsWith(USER_CENTER_REFERER_PREFIX)) {
                return USER_CENTER_LOCATION;
            }
        } catch (RuntimeException ignored) {
            return null;
        }
        return null;
    }

    private static boolean canAccessConsole(List<String> permissions) {
        return permissions.contains(ALL_UI_PERMISSIONS)
                || permissions.stream().anyMatch(CONSOLE_UI_PERMISSIONS::contains);
    }

    private Mono<Void> redirect(ServerWebExchange exchange, String location) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(location));
        response.getHeaders().setCacheControl("no-store");
        return response.setComplete();
    }
}
