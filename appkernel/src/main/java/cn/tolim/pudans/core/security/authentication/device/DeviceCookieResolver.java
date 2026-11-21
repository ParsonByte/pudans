package cn.tolim.pudans.core.security.authentication.device;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpCookie;
import org.springframework.web.server.ServerWebExchange;

public interface DeviceCookieResolver {

    @Nullable
    HttpCookie resolveCookie(ServerWebExchange exchange);

    void setCookie(ServerWebExchange exchange, String value);

    String getCookieName();

    Duration getCookieMaxAge();
}
