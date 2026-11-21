package cn.tolim.pudans.infra.setting;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.session.data.redis.ReactiveRedisIndexedSessionRepository;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterSettingSessionTimeoutApplier {

    private final ParameterGroups parameterGroups;

    private final ReactiveRedisIndexedSessionRepository sessionRepository;

    // 扩展初始化后应用访问令牌有效期。
    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        apply();
    }

    // 仅当服务器组变化时重新应用会话超时。
    @EventListener
    void onParameterSettingChanged(ParameterSettingChangedEvent event) {
        if (java.util.Objects.equals(
                event.getOldData().get("server"), event.getNewData().get("server"))) {
            return;
        }
        apply();
    }

    // 读取服务器设置并应用访问令牌有效期。
    void apply() {
        parameterGroups.getServer().subscribe(
                server -> apply(server.getAccessTokenTtlSeconds()),
                error -> log.warn("无法读取访问令牌 TTL", error));
    }

    // 设置会话默认过期时间，并保证最小为一分钟。
    void apply(Integer accessTokenTtlSeconds) {
        if (accessTokenTtlSeconds == null) {
            return;
        }
        var seconds = Math.max(60, accessTokenTtlSeconds);
        sessionRepository.setDefaultMaxInactiveInterval(Duration.ofSeconds(seconds));
        log.info("已将访问令牌 TTL 设置为 {} 秒，应用于新会话", seconds);
    }
}
