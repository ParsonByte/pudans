package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.core.user.service.UserConnectionService;
import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationTrustResolver;
import org.springframework.security.authentication.AuthenticationTrustResolverImpl;
import org.springframework.security.core.Authentication;
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

    private final UserConnectionService connectionService;

    private final ServerSecurityContextRepository securityContextRepository;

    private final OAuth2AuthenticationTokenCache authenticationCache;

    private final OAuth2AuthenticationSession authenticationSession;

    private final ServerLogoutHandler logoutHandler;

    private final ServerResponse.Context context;

    @Setter
    private AuthenticationTrustResolver authenticationTrustResolver = new AuthenticationTrustResolverImpl();

    public MapOAuth2AuthenticationFilter(
            ServerSecurityContextRepository securityContextRepository,
            UserConnectionService connectionService,
            OAuth2AuthenticationSession authenticationSession,
            OAuth2AuthenticationTokenCache authenticationCache,
            ServerResponse.Context context) {
        this.connectionService = connectionService;
        this.securityContextRepository = securityContextRepository;
        this.authenticationSession = authenticationSession;
        this.authenticationCache = authenticationCache;
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
                .doOnNext(

                        authentication -> exchange.getAttributes().put(
                                OAuth2LoginAttributes.PRE_AUTHENTICATION, authentication))
                .then(chain.filter(exchange))
                .then(Mono.defer(() -> securityContextRepository
                        .load(exchange)
                        .switchIfEmpty(ReactiveSecurityContextHolder.getContext())
                        .mapNotNull(SecurityContext::getAuthentication)
                        .filter(OAuth2AuthenticationToken.class::isInstance)
                        .cast(OAuth2AuthenticationToken.class)
                        .flatMap(oauth2Token -> {
                            var registrationId = oauth2Token.getAuthorizedClientRegistrationId();
                            var oauth2User = oauth2Token.getPrincipal();
                            return connectionService
                                    .updateUserConnectionIfPresent(registrationId, oauth2User)
                                    .switchIfEmpty(Mono.defer(
                                            () -> Mono.justOrEmpty(
                                                            (Object) exchange.getAttribute(
                                                                    OAuth2LoginAttributes.PRE_AUTHENTICATION))
                                                    .filter(Authentication.class::isInstance)
                                                    .cast(Authentication.class)
                                                    .filter(authenticationTrustResolver::isAuthenticated)
                                                    .flatMap(preAuth -> connectionService.createUserConnection(
                                                            preAuth.getName(), registrationId, oauth2User))))
                                    .switchIfEmpty(Mono.defer(() -> {

                                        return authenticationCache
                                                .saveToken(exchange, oauth2Token)
                                                .then(Mono.defer(() -> {
                                                    var webFilterExchange = new WebFilterExchange(exchange, chain);

                                                    return logoutHandler.logout(webFilterExchange, oauth2Token);
                                                }))
                                                .then(Mono.defer(() -> ProblemDetailResponses.write(
                                                        exchange,
                                                        context,
                                                        HttpStatus.UNAUTHORIZED,
                                                        "oauth2-connection-required",
                                                        "请先绑定该第三方账号。")))
                                                .then(Mono.empty());
                                    }))

                                    .flatMap(connection -> authenticationSession.establish(
                                            exchange, connection.getSpec().getUsername(), oauth2Token));
                        })
                        .then()));
    }
}
