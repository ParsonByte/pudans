package cn.tolim.pudans.core.security.authentication;

import static org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers;

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

    private static ServerWebExchangeMatcher tokenAuthMatcher() {
        var bearerTokenConverter = new ServerBearerTokenAuthenticationConverter();
        return exchange -> bearerTokenConverter
                .convert(exchange)
                .flatMap(a -> ServerWebExchangeMatcher.MatchResult.match())
                .switchIfEmpty(Mono.defer(ServerWebExchangeMatcher.MatchResult::notMatch));
    }

    @Override
    public void configure(ServerHttpSecurity http) {
        var csrfMatcher = new AndServerWebExchangeMatcher(
                CsrfWebFilter.DEFAULT_CSRF_MATCHER,
                new NegatedServerWebExchangeMatcher(pathMatchers("/actuator/**", "/sudo/**", "/login", "/logout")),
                new NegatedServerWebExchangeMatcher(tokenAuthMatcher()));
        http.csrf(csrfSpec -> csrfSpec
                .csrfTokenRepository(CookieServerCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new ServerCsrfTokenRequestAttributeHandler())
                .requireCsrfProtectionMatcher(csrfMatcher));
        // CSRF 过滤器之后补一个订阅点，确保安全方法请求也会写入 XSRF-TOKEN Cookie
        http.addFilterAfter(new CsrfTokenCookieWebFilter(), SecurityWebFiltersOrder.CSRF);
    }
}
