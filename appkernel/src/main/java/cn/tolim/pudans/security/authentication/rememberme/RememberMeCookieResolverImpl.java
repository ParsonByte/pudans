package cn.tolim.pudans.security.authentication.rememberme;

import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.security.authentication.CookieSupport;
import java.time.Duration;
import lombok.Getter;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpCookie;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Getter
@Component
class RememberMeCookieResolverImpl implements RememberMeCookieResolver {
    public static final String SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY = "remember-me";

    private final String cookieName = SPRING_SECURITY_REMEMBER_ME_COOKIE_KEY;

    private final Duration cookieMaxAge;

    public RememberMeCookieResolverImpl(PudansProperties pudansProperties) {
        this.cookieMaxAge = pudansProperties.getSecurity().getRememberMe().getTokenValidity();
    }

    @Override
    @Nullable
    public HttpCookie resolveRememberMeCookie(ServerWebExchange exchange) {
        return exchange.getRequest().getCookies().getFirst(getCookieName());
    }

    @Override
    public void setRememberMeCookie(ServerWebExchange exchange, String value) {
        Assert.notNull(value, "'value' 不能为空");
        exchange.getResponse()
                .beforeCommit(() -> Mono.fromRunnable(() -> {
                    var cookie = CookieSupport.initCookie(exchange, getCookieName(), getCookieMaxAge(), value)
                            .build();
                    exchange.getResponse().addCookie(cookie);
                }));
    }

    @Override
    public void expireCookie(ServerWebExchange exchange) {
        exchange.getResponse()
                .beforeCommit(() -> Mono.fromRunnable(() -> {
                    var cookie = CookieSupport.initCookie(exchange, getCookieName(), getCookieMaxAge(), "")
                            .maxAge(0)
                            .build();
                    exchange.getResponse().getCookies().set(this.cookieName, cookie);
                }));
    }
}
