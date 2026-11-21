package cn.tolim.pudans.security.authentication.device;

import cn.tolim.pudans.security.api.device.DeviceSessions;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
class DeviceSessionFilter implements WebFilter {

    private final DeviceSessions deviceSessions;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return exchange.getSession().flatMap(session -> {
            var previousId = session.getId();
            return chain.filter(exchange).then(Mono.defer(() -> {
                var currentId = session.getId();
                if (Objects.equals(previousId, currentId)) {
                    return Mono.empty();
                }

                log.debug("会话 ID 从 {} 变为 {}，正在更新设备信息。", previousId, currentId);
                return deviceSessions.changeSessionId(exchange);
            }));
        });
    }
}
