package cn.tolim.pudans.core.security.authentication.rememberme;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher.MatchResult;

import cn.tolim.pudans.core.infra.exception.ProblemDetailResponses;
import cn.tolim.pudans.core.security.authentication.SecurityConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.context.ServerSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerResponse;

@Component
@RequiredArgsConstructor
@Order(0)
public class RememberMeConfigurer implements SecurityConfigurer {

    private final RememberMeServices rememberMeServices;

    private final ServerSecurityContextRepository securityContextRepository;

    private final CookieSignatureKeyResolver cookieSignatureKeyResolver;

    private final ServerResponse.Context context;

    @Override
    public void configure(ServerHttpSecurity http) {
        var authManager = new RememberMeAuthenticationManager(cookieSignatureKeyResolver);
        var filter = new AuthenticationWebFilter(authManager);
        filter.setSecurityContextRepository(securityContextRepository);
        filter.setAuthenticationFailureHandler((exchange, exception) ->
                rememberMeServices
                        .loginFail(exchange.getExchange())
                        .then(ProblemDetailResponses.writeOrRedirect(
                                exchange.getExchange(),
                                context,
                                HttpStatus.UNAUTHORIZED,
                                "remember-me-failed",
                                "记住我登录失败，请重新登录。",
                                ProblemDetailResponses.SIGN_IN_PATH)));
        filter.setServerAuthenticationConverter(rememberMeServices::autoLogin);
        filter.setRequiresAuthenticationMatcher(exchange -> ReactiveSecurityContextHolder.getContext()
                .flatMap(securityContext -> MatchResult.notMatch())
                .switchIfEmpty(MatchResult.match()));
        http.addFilterAt(filter, SecurityWebFiltersOrder.AUTHENTICATION);
    }
}
