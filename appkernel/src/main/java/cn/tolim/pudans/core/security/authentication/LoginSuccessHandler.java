package cn.tolim.pudans.core.security.authentication;

import static cn.tolim.pudans.core.security.authentication.SecurityConstant.REMEMBER_ME_PARAMETER_NAME;

import cn.tolim.pudans.core.audit.AuditLog;
import cn.tolim.pudans.core.user.RefreshTokens;
import cn.tolim.pudans.core.infra.utils.PudansUtils;
import cn.tolim.pudans.core.security.authentication.twofactor.TwoFactorAuthentication;
import cn.tolim.pudans.core.security.authentication.login.PudansUser;
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

    private final RefreshTokens refreshTokens;

    public LoginSuccessHandler(
            ServerResponse.Context context,
            LoginFlowHandler loginFlowHandler,
            LoginParameterRequestCache parameterRequestCache,
            ServerRequestCache serverRequestCache,
            String postLoginUrl,
            RefreshTokens refreshTokens) {
        this.context = context;
        this.loginFlowHandler = loginFlowHandler;
        this.parameterRequestCache = parameterRequestCache;
        var successHandler = new RedirectServerAuthenticationSuccessHandler(postLoginUrl);
        successHandler.setRequestCache(serverRequestCache);
        this.defaultSuccessHandler = successHandler;
        this.refreshTokens = refreshTokens;
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

        ServerWebExchangeMatcher xhrMatcher = exchange -> PudansUtils.isXhr(
                        exchange.getRequest().getHeaders())
                ? ServerWebExchangeMatcher.MatchResult.match()
                : ServerWebExchangeMatcher.MatchResult.notMatch();

        var exchange = webFilterExchange.getExchange();
        exchange.getAttributes().put(AuditLog.USERNAME_ATTRIBUTE, authentication.getName());
        exchange.getAttributes().put(AuditLog.OPERATION_ATTRIBUTE, "LOGIN");
        exchange.getAttributes().put(AuditLog.RESULT_ATTRIBUTE, "SUCCESS");
        exchange.getAttributes().put(AuditLog.CONTEXT_ATTRIBUTE, "local");
        return loginFlowHandler
                .onLoginSuccess(exchange, authentication)
                .then(xhrMatcher
                        .matches(exchange)
                        .filter(ServerWebExchangeMatcher.MatchResult::isMatch)
                        .switchIfEmpty(Mono.defer(() -> defaultSuccessHandler
                                .onAuthenticationSuccess(webFilterExchange, authentication)
                                .then(Mono.empty())))
                        .flatMap(isXhr -> refreshTokens
                                // 仅本地密码登录签发刷新令牌；Passkey/OAuth2 保持原有响应。
                                .issue(authentication.getName())
                                .onErrorResume(error -> {
                                    log.warn("无法签发刷新令牌", error);
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
