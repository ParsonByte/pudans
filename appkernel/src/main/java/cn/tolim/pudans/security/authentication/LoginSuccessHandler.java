package cn.tolim.pudans.security.authentication;

import static cn.tolim.pudans.security.authentication.SecurityConstant.REMEMBER_ME_PARAMETER_NAME;

import cn.tolim.pudans.contract.security.LoginHandlerEnhancer;
import cn.tolim.pudans.core.audit.service.AuditService;
import cn.tolim.pudans.security.authentication.twofactor.TwoFactorAuthentication;
import java.net.URI;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.web.server.DefaultServerRedirectStrategy;
import org.springframework.security.web.server.ServerRedirectStrategy;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.RedirectServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.savedrequest.ServerRequestCache;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;


public class LoginSuccessHandler implements ServerAuthenticationSuccessHandler {

    private final ServerResponse.Context context;

    private final LoginHandlerEnhancer loginHandlerEnhancer;

    private final LoginParameterRequestCache parameterRequestCache;

    private final ServerRedirectStrategy redirectStrategy = new DefaultServerRedirectStrategy();

    private final ServerAuthenticationSuccessHandler defaultSuccessHandler;

    public LoginSuccessHandler(
            ServerResponse.Context context,
            LoginHandlerEnhancer loginHandlerEnhancer,
            LoginParameterRequestCache parameterRequestCache,
            ServerRequestCache serverRequestCache) {
        this.context = context;
        this.loginHandlerEnhancer = loginHandlerEnhancer;
        this.parameterRequestCache = parameterRequestCache;
        var successHandler = new RedirectServerAuthenticationSuccessHandler("/uc/");
        successHandler.setRequestCache(serverRequestCache);
        this.defaultSuccessHandler = successHandler;
    }

    @Override
    public Mono<Void> onAuthenticationSuccess(WebFilterExchange webFilterExchange, Authentication authentication) {
        if (authentication instanceof TwoFactorAuthentication) {
            var exchange = webFilterExchange.getExchange();
            return parameterRequestCache
                    .saveParameter(exchange, REMEMBER_ME_PARAMETER_NAME)
                    .then(redirectStrategy.sendRedirect(exchange, URI.create("/challenges/two-factor/totp")));
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
        return loginHandlerEnhancer
                .onLoginSuccess(exchange, authentication)
                .then(xhrMatcher
                        .matches(exchange)
                        .filter(ServerWebExchangeMatcher.MatchResult::isMatch)
                        .switchIfEmpty(Mono.defer(() -> defaultSuccessHandler
                                .onAuthenticationSuccess(webFilterExchange, authentication)
                                .then(Mono.empty())))
                        .flatMap(isXhr -> ServerResponse.ok()
                                .bodyValue(authentication.getPrincipal())
                                .flatMap(response -> response.writeTo(exchange, context))));
    }
}
