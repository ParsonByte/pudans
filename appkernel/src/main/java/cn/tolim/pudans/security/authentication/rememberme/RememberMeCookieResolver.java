package cn.tolim.pudans.security.authentication.rememberme;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpCookie;
import org.springframework.web.server.ServerWebExchange;

public interface RememberMeCookieResolver {

    @Nullable
    HttpCookie resolveRememberMeCookie(ServerWebExchange exchange);

    void setRememberMeCookie(ServerWebExchange exchange, String value);

    void expireCookie(ServerWebExchange exchange);

    String getCookieName();

    Duration getCookieMaxAge();
}
