package cn.tolim.pudans.core.ai.routing;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

// 记录最近被限流的供应商账号，供开启 sink 的路由组排除候选。
@Component
public class SunkRouteRegistry {

    private static final Duration SINK_TTL = Duration.ofSeconds(60);

    private final Map<String, Long> sunkAt = new ConcurrentHashMap<>();

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public void markSunk(String provider, String account) {
        if (provider != null && account != null) {
            sunkAt.put(key(provider, account), clock.millis());
        }
    }

    public boolean isSunk(String provider, String account) {
        if (provider == null || account == null) {
            return false;
        }
        var at = sunkAt.get(key(provider, account));
        if (at == null) {
            return false;
        }
        if (clock.millis() - at >= SINK_TTL.toMillis()) {
            sunkAt.remove(key(provider, account));
            return false;
        }
        return true;
    }

    private static String key(String provider, String account) {
        return provider + '/' + account;
    }
}