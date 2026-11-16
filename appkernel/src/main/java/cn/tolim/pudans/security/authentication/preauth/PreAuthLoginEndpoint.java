package cn.tolim.pudans.security.authentication.preauth;

import static cn.tolim.pudans.security.authentication.SecurityConstant.REMEMBER_ME_PARAMETER_NAME;
import static org.springframework.web.reactive.function.server.RequestPredicates.path;

import cn.tolim.pudans.contract.core.extension.AuthProvider;
import cn.tolim.pudans.infra.exception.ProblemDetailFactory;
import cn.tolim.pudans.infra.utils.PudansUtils;
import cn.tolim.pudans.security.authentication.AuthProviderService;
import cn.tolim.pudans.security.authentication.LoginParameterRequestCache;
import java.net.URI;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

    private static final String LOGIN_PAGE_REMOVED = "login-page-removed";

    private final AuthProviderService authProviderService;

    private final LoginParameterRequestCache parameterRequestCache;

    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    RouterFunction<ServerResponse> preAuthLoginEndpoints() {
        return RouterFunctions.nest(
                path("/login"),
                RouterFunctions.route()
                        .GET("", request -> ServerResponse.status(HttpStatus.GONE)
                                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                                .bodyValue(ProblemDetailFactory.create(
                                        HttpStatus.GONE,
                                        LOGIN_PAGE_REMOVED,
                                        "The sign-in page is not served by the backend.")))
                        .POST("/social/{authProviderName}", this::socialLogin)
                        .before(PudansUtils.noCache())
                        .build());
    }

    private Mono<ServerResponse> socialLogin(ServerRequest request) {
        var authProviderName = request.pathVariable("authProviderName");
        return authProviderService
                .getEnabledProviders()
                .filter(ap -> Objects.equals(authProviderName, ap.getMetadata().getName()))
                .filter(ap -> !AuthProvider.AuthType.FORM.equals(ap.getSpec().getAuthType()))
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
    }
}
