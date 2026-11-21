package cn.tolim.pudans.core.security.authentication.device;

import cn.tolim.pudans.core.security.authentication.CookieSupport;
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
class DeviceCookieResolverImpl implements DeviceCookieResolver {
    public static final String DEVICE_COOKIE_KEY = "device_id";

    private final String cookieName = DEVICE_COOKIE_KEY;

    private final Duration cookieMaxAge = Duration.ofDays(100);

    @Override
    @Nullable
    public HttpCookie resolveCookie(ServerWebExchange exchange) {
        return exchange.getRequest().getCookies().getFirst(getCookieName());
    }

    @Override
    public void setCookie(ServerWebExchange exchange, String value) {
        Assert.notNull(value, "'value' 不能为空");
        exchange.getResponse()
                .beforeCommit(() -> Mono.fromRunnable(() -> {
                    var deviceCookie = CookieSupport.initCookie(exchange, getCookieName(), getCookieMaxAge(), value)
                            .build();
                    exchange.getResponse().addCookie(deviceCookie);
                }));
    }
}
