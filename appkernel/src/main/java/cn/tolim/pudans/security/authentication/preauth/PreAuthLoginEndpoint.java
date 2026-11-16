package cn.tolim.pudans.security.authentication.preauth;

import static cn.tolim.pudans.security.authentication.SecurityConstant.REMEMBER_ME_PARAMETER_NAME;
import static cn.tolim.pudans.security.authentication.oauth2.OAuth2IdentityFingerprint.PARAMETER_NAME;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

import cn.tolim.pudans.contract.core.extension.AuthProvider;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.contract.security.authentication.CryptoService;
import cn.tolim.pudans.infra.actuator.GlobalInfoService;
import cn.tolim.pudans.infra.utils.PudansUtils;
import cn.tolim.pudans.security.authentication.AuthProviderService;
import cn.tolim.pudans.security.authentication.LoginParameterRequestCache;
import cn.tolim.pudans.security.authentication.PudansServerRequestCache;
import cn.tolim.pudans.security.authentication.oauth2.OAuth2AuthenticationTokenCache;
import cn.tolim.pudans.security.authentication.oauth2.OAuth2BindIntent;
import cn.tolim.pudans.security.authentication.oauth2.OAuth2IdentityFingerprint;
import java.net.URI;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
class PreAuthLoginEndpoint {

    private final CryptoService cryptoService;

    private final GlobalInfoService globalInfoService;

    private final AuthProviderService authProviderService;

    private final ServerRequestCache serverRequestCache = new PudansServerRequestCache();

    private final LoginParameterRequestCache parameterRequestCache;

    private final OAuth2AuthenticationTokenCache oauth2TokenCache;

    private final OAuth2BindIntent bindIntent;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> preAuthLoginEndpoints() {
        return RouterFunctions.nest(
                path("/login"),
                RouterFunctions.route()
                        .GET("", request -> ServerResponse.ok()
                                .contentType(org.springframework.http.MediaType.TEXT_HTML)
                                .bodyValue("<html><body><h1>pudans</h1>"
                                        + "<form method='post' action='/login'>"
                                        + "<input name='username' placeholder='Username'>"
                                        + "<input type='password' name='password' placeholder='Password'>"
                                        + "<button type='submit'>Sign in</button></form>"
                                        + "<a href='" + ApiGroups.API_PREFIX
                                        + "/oauth2/authorization/feishu'>Sign in with Feishu</a>"
                                        + "</body></html>"))
                        .POST("/social/{authProviderName}", request -> {
                            var authProviderName = request.pathVariable("authProviderName");
                            return authProviderService
                                    .getEnabledProviders()
                                    .filter(ap -> Objects.equals(
                                            authProviderName, ap.getMetadata().getName()))
                                    .filter(ap -> !AuthProvider.AuthType.FORM.equals(
                                            ap.getSpec().getAuthType()))
                                    .next()
                                    .switchIfEmpty(Mono.error(() -> new ServerWebInputException(
                                            "Auth provider " + authProviderName + " not found or not enabled.")))
                                    .flatMap(ap -> {
                                        var authenticationUrl = ap.getSpec().getAuthenticationUrl();
                                        return parameterRequestCache
                                                .saveParameter(request.exchange(), REMEMBER_ME_PARAMETER_NAME)
                                                .then(Mono.defer(() -> ServerResponse.status(HttpStatus.FOUND)
                                                        .location(URI.create(authenticationUrl))
                                                        .build()));
                                    });
                        })
                        .before(PudansUtils.noCache())
                        .build());
    }

    private Mono<Boolean> prepareOAuth2Continuation(ServerRequest request) {
        if (request.queryParam("oauth2_bind").isPresent()) {
            var exchange = request.exchange();
            var expectedIdentity = request.queryParam(PARAMETER_NAME).orElse(null);
            return oauth2TokenCache
                    .getToken(exchange)
                    .filter(token -> Objects.equals(expectedIdentity, OAuth2IdentityFingerprint.from(token)))
                    .flatMap(
                            token -> bindIntent.save(exchange, expectedIdentity).thenReturn(true))
                    .switchIfEmpty(Mono.defer(
                            () -> Mono.when(oauth2TokenCache.removeToken(exchange), bindIntent.clear(exchange))
                                    .thenReturn(false)));
        }
        if (request.queryParam("oauth2_select").isPresent()) {
            return bindIntent.clear(request.exchange()).thenReturn(true);
        }
        return Mono.when(oauth2TokenCache.removeToken(request.exchange()), bindIntent.clear(request.exchange()))
                .thenReturn(true);
    }
}
