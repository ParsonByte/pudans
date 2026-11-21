package cn.tolim.pudans.security.authentication.device;

import java.time.Duration;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpCookie;
import org.springframework.web.server.ServerWebExchange;

public interface DeviceCookieResolver {

    // 读取请求携带的设备标识，未携带时返回空值
    @Nullable
    HttpCookie resolveCookie(ServerWebExchange exchange);

    // 在响应提交前写入标识，保证浏览器能收到变更
    void setCookie(ServerWebExchange exchange, String value);

    // 暴露标识字段名，确保读写两处使用同一名称
    String getCookieName();

    // 暴露标识有效期，避免过期策略散落各处
    Duration getCookieMaxAge();
}
