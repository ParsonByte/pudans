package cn.tolim.pudans.security.authentication.oauth2;

import cn.tolim.pudans.core.user.service.UserConnectionService;
import cn.tolim.pudans.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

final class OAuth2LoginSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final UserConnectionService connectionService;

    private final OAuth2AuthenticationSession authenticationSession;

    private final SecurityProperties securityProperties;

    private final ServerResponse.Context context;

    OAuth2LoginSuccessHandler(
            UserConnectionService connectionService,
            OAuth2AuthenticationSession authenticationSession,
            SecurityProperties securityProperties,
            ServerResponse.Context context) {
        this.connectionService = connectionService;
        this.authenticationSession = authenticationSession;
        this.securityProperties = securityProperties;
        this.context = context;
    }

    private static Mono<Void> redirect(ServerWebExchange exchange, String location) {
        var response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FOUND);
        response.getHeaders().setLocation(URI.create(location));
        return response.setComplete();
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(
            WebFilterExchange webFilterExchange, Authentication authentication) {
        var exchange = webFilterExchange.getExchange();
        var oauth2 = (OAuth2AuthenticationToken) authentication;
        var registrationId = oauth2.getAuthorizedClientRegistrationId();
        var oauth2User = oauth2.getPrincipal();
        var preAuthentication = exchange.getAttribute(OAuth2LoginAttributes.PRE_AUTHENTICATION);
        var twoFactorSatisfied = OAuth2TwoFactorSupport.twoFactorSatisfied(preAuthentication);
        return connectionService
                .updateUserConnectionIfPresent(registrationId, oauth2User)
                .switchIfEmpty(Mono.defer(() -> preAuthentication instanceof Authentication preAuth
                        && preAuth.isAuthenticated()
                        ? connectionService.createUserConnection(
                                preAuth.getName(), registrationId, oauth2User)
                        : Mono.error(OAuth2ConnectionRequiredException::new)))
                .flatMap(connection -> authenticationSession.establishAndGetAuthentication(
                        exchange,
                        connection.getSpec().getUsername(),
                        oauth2,
                        twoFactorSatisfied))
                .flatMap(result -> result instanceof TwoFactorAuthentication
                        ? ProblemDetailResponses.writeOrRedirect(
                                exchange,
                                context,
                                HttpStatus.UNAUTHORIZED,
                                "two-factor-required",
                                "该账号需要完成双重验证，请使用密码登录。",
                                ProblemDetailResponses.SIGN_IN_PATH)
                        : redirect(exchange, securityProperties.getPostLoginUrl()))
                .onErrorResume(
                        OAuth2ConnectionRequiredException.class,
                        error -> ProblemDetailResponses.writeOrRedirect(
                                exchange,
                                context,
                                HttpStatus.UNAUTHORIZED,
                                "oauth2-connection-required",
                                "请先绑定该第三方账号。",
                                ProblemDetailResponses.SIGN_IN_PATH));
    }

    private static final class OAuth2ConnectionRequiredException extends RuntimeException {}
}
