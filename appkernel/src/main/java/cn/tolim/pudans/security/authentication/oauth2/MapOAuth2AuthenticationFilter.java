package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.core.user.Connections;
import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.logout.SecurityContextServerLogoutHandler;
import org.springframework.security.web.server.authentication.logout.ServerLogoutHandler;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;


class MapOAuth2AuthenticationFilter implements WebFilter {

    private final OAuth2ConnectionMapping connectionMapping;

    private final ServerSecurityContextRepository securityContextRepository;

    private final OAuth2AuthenticationSession authenticationSession;

    private final ServerLogoutHandler logoutHandler;

    private final ServerResponse.Context context;

    @Setter
    private AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    public MapOAuth2AuthenticationFilter(
            ServerSecurityContextRepository securityContextRepository,
            Connections connections,
            OAuth2AuthenticationSession authenticationSession,
            ServerResponse.Context context) {
        this.connectionMapping = new OAuth2ConnectionMapping(connections);
        this.securityContextRepository = securityContextRepository;
        this.authenticationSession = authenticationSession;
        this.context = context;
        var logoutHandler = new SecurityContextServerLogoutHandler();
        logoutHandler.setSecurityContextRepository(securityContextRepository);
        this.logoutHandler = logoutHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return ReactiveSecurityContextHolder.getContext()
                .mapNotNull(SecurityContext::getAuthentication)
                .filter(authenticationTrustResolver::isAuthenticated)
                .filter(authentication -> !(authentication instanceof OAuth2AuthenticationToken))
                .doOnNext(authentication -> exchange.getAttributes().put(
                        OAuth2LoginAttributes.PRE_AUTHENTICATION, authentication))
                .then(chain.filter(exchange))
                .then(Mono.defer(() -> securityContextRepository
                        .load(exchange)
                        .switchIfEmpty(ReactiveSecurityContextHolder.getContext())
                        .mapNotNull(SecurityContext::getAuthentication)
                        .filter(OAuth2AuthenticationToken.class::isInstance)
                        .cast(OAuth2AuthenticationToken.class)
                        .flatMap(oauth2Token -> connectionMapping
                                .map(exchange, oauth2Token)
                                .flatMap(connection -> authenticationSession.establish(
                                        exchange,
                                        connection.getSpec().getUsername(),
                                        oauth2Token))
                                .onErrorResume(
                                        OAuth2ConnectionMapping.ConnectionRequiredException.class,
                                        error -> logoutAndRequireConnection(exchange, chain, oauth2Token))
                                .onErrorResume(
                                        AuthenticationException.class,
                                        error -> OAuth2AuthenticationSession.rejectUnavailableAccount(
                                                exchange, context)))
                        .then()));
    }

    private Mono<Void> logoutAndRequireConnection(
            ServerWebExchange exchange, WebFilterChain chain, OAuth2AuthenticationToken token) {
        var webFilterExchange = new WebFilterExchange(exchange, chain);
        return logoutHandler
                .logout(webFilterExchange, token)
                .then(ProblemDetailResponses.writeOrRedirect(
                        exchange,
                        context,
                        HttpStatus.UNAUTHORIZED,
                        "oauth2-connection-required",
                        "请先绑定该第三方账号。",
                        ProblemDetailResponses.SIGN_IN_PATH))
                .then();
    }
}
