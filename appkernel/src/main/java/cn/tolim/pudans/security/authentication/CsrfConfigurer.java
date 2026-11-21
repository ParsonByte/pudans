package cn.tolim.pudans.security.authentication;

import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.web.server.authentication.ServerBearerTokenAuthenticationConverter;
import org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.csrf.ServerCsrfTokenRequestAttributeHandler;
import org.springframework.security.web.server.util.matcher.AndServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.NegatedServerWebExchangeMatcher;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatcher;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Order(0)
class CsrfConfigurer implements SecurityConfigurer {

    // 识别令牌认证请求，此类请求不参与 CSRF 校验。
    private static ServerWebExchangeMatcher tokenAuthMatcher() {
        var bearerTokenConverter = new ServerBearerTokenAuthenticationConverter();
        return exchange -> bearerTokenConverter
                .convert(exchange)
                .flatMap(a -> ServerWebExchangeMatcher.MatchResult.match())
                .switchIfEmpty(Mono.defer(ServerWebExchangeMatcher.MatchResult::notMatch));
    }

    // 启用 Cookie 形式的 CSRF 令牌，并补充安全方法的下发过滤器。
    @Override
    public void configure(ServerHttpSecurity http) {
        http.csrf(csrfSpec -> csrfSpec
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(csrfMatcher()));
        // CSRF 过滤器之后补一个订阅点，确保安全方法请求也会写入 XSRF-TOKEN Cookie
        http.addFilterAfter(new CsrfTokenCookieWebFilter(), SecurityWebFiltersOrder.CSRF);
    }

    // 所有 Cookie 会话写操作都需要 CSRF 令牌，仅 Bearer 令牌请求除外。
    static ServerWebExchangeMatcher csrfMatcher() {
        return new AndServerWebExchangeMatcher(
                CsrfWebFilter.DEFAULT_CSRF_MATCHER,
                new NegatedServerWebExchangeMatcher(tokenAuthMatcher()));
    }
}
