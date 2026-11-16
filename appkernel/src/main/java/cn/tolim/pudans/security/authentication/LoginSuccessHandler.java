package cn.tolim.pudans.security.authentication;

import static cn.tolim.pudans.security.authentication.SecurityConstant.REMEMBER_ME_PARAMETER_NAME;

import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.core.user.service.RefreshTokenService;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import cn.tolim.pudans.security.authentication.login.PudansUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;


@Slf4j
public class LoginSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final ServerResponse.Context context;

    private final LoginFlowHandler loginFlowHandler;

    private final LoginParameterRequestCache parameterRequestCache;

    private final ServerAuthenticationSuccessHandler defaultSuccessHandler;

    private final RefreshTokenService refreshTokenService;

    public LoginSuccessHandler(
            ServerResponse.Context context,
            LoginFlowHandler loginFlowHandler,
            LoginParameterRequestCache parameterRequestCache,
            ServerRequestCache serverRequestCache,
            String postLoginUrl,
            RefreshTokenService refreshTokenService) {
        this.context = context;
        this.loginFlowHandler = loginFlowHandler;
        this.parameterRequestCache = parameterRequestCache;
        var successHandler = new RedirectServerAuthenticationSuccessHandler(postLoginUrl);
        successHandler.setRequestCache(serverRequestCache);
        this.defaultSuccessHandler = successHandler;
        this.refreshTokenService = refreshTokenService;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        if (authentication instanceof TwoFactorAuthentication) {
            var exchange = webFilterExchange.getExchange();
            return parameterRequestCache
                    .saveParameter(exchange, REMEMBER_ME_PARAMETER_NAME)
                    .then(writeTwoFactorRequired(exchange, authentication));
        }

        if (authentication instanceof CredentialsContainer container) {
            container.eraseCredentials();
        }

        ServerWebExchangeMatcher xhrMatcher = exchange -> {
            if (exchange.getRequest()
                    .getHeaders()
                    .getOrEmpty("X-Requested-With")
                    .contains("XMLHttpRequest")) {
                return ServerWebExchangeMatcher.MatchResult.match();
            }
            return ServerWebExchangeMatcher.MatchResult.notMatch();
        };

        var exchange = webFilterExchange.getExchange();
        exchange.getAttributes().put(AuditService.USERNAME_ATTRIBUTE, authentication.getName());
        exchange.getAttributes().put(AuditService.OPERATION_ATTRIBUTE, "LOGIN");
        exchange.getAttributes().put(AuditService.RESULT_ATTRIBUTE, "SUCCESS");
        exchange.getAttributes().put(AuditService.CONTEXT_ATTRIBUTE, "local");
        return loginFlowHandler
                .onLoginSuccess(exchange, authentication)
                .then(xhrMatcher
                        .matches(exchange)
                        .filter(ServerWebExchangeMatcher.MatchResult::isMatch)
                        .switchIfEmpty(Mono.defer(() -> defaultSuccessHandler
                                .onAuthenticationSuccess(webFilterExchange, authentication)
                                .then(Mono.empty())))
                        .flatMap(isXhr -> refreshTokenService
                                // Refresh tokens: local password login only; passkey/OAuth2 keep their responses.
                                .issue(authentication.getName())
                                .onErrorResume(error -> {
                                    log.warn("Unable to issue a refresh token", error);
                                    return Mono.just("");
                                })
                                .flatMap(refreshToken -> {
                                    if (authentication.getPrincipal() instanceof PudansUser user) {
                                        user.setRefreshToken(refreshToken);
                                    }
                                    return ServerResponse.ok()
                                            .bodyValue(authentication.getPrincipal())
                                            .flatMap(response -> response.writeTo(exchange, context));
                                })));
    }

    private Mono<Void> writeTwoFactorRequired(ServerWebExchange exchange, Authentication authentication) {
        return ServerResponse.ok()
                .bodyValue(new TwoFactorRequiredView(true, authentication.getName()))
                .flatMap(response -> response.writeTo(exchange, context));
    }

    private record TwoFactorRequiredView(boolean twoFactorRequired, String username) {}
}
