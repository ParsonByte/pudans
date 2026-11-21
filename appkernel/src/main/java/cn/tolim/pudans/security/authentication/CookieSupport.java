package cn.tolim.pudans.security.authentication;

import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.web.server.ServerWebExchange;

public final class CookieSupport {

    private CookieSupport() {}

    // 统一 Cookie 属性，路径跟随上下文，HTTPS 下仅安全传输。
    public static ResponseCookie.ResponseCookieBuilder initCookie(
            ServerWebExchange exchange, String name, Duration maxAge, String value) {
        return ResponseCookie.from(name, value)
                .path(exchange.getRequest().getPath().contextPath().value() + "/")
                .maxAge(maxAge)
                .httpOnly(true)
                .secure("https".equalsIgnoreCase(exchange.getRequest().getURI().getScheme()))
                .sameSite("Lax");
    }
}
