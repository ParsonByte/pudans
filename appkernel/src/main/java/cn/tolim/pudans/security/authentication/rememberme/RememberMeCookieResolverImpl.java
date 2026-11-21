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

    // 从安全配置读取令牌有效期作为 Cookie 生存时间。
    public RememberMeCookieResolverImpl(PudansProperties pudansProperties) {
        this.cookieMaxAge = pudansProperties.getSecurity().getRememberMe().getTokenValidity();
    }

    // 只读取第一个同名 Cookie，避免重复值干扰。
    @Override
    @Nullable
    public HttpCookie resolveRememberMeCookie(ServerWebExchange exchange) {
        return exchange.getRequest().getCookies().getFirst(getCookieName());
    }

    // 延迟到响应提交前写入，确保 Cookie 与响应一致。
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

    // 延迟写入过期 Cookie，使浏览器清除记住我状态。
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
