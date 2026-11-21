package cn.tolim.pudans.security.authentication.rememberme;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpCookie;
import org.springframework.web.server.ServerWebExchange;

public interface RememberMeCookieResolver {

    // 读取请求中的记住我 Cookie，不存在时返回空。
    @Nullable
    HttpCookie resolveRememberMeCookie(ServerWebExchange exchange);

    // 在响应提交前写入 Cookie，保证响应头完整。
    void setRememberMeCookie(ServerWebExchange exchange, String value);

    // 以零有效期覆盖 Cookie，通知浏览器删除。
    void expireCookie(ServerWebExchange exchange);

    // 返回固定 Cookie 名称，供读写与清理统一使用。
    String getCookieName();

    // 返回令牌有效期，同时用于 Cookie 与过期判断。
    Duration getCookieMaxAge();
}
