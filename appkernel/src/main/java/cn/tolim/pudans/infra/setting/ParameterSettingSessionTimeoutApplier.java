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

    private static final long DEFAULT_ACCESS_TOKEN_TTL_SECONDS = 1800;

    private final ParameterSettingGroupService parameterSettingGroupService;

    private final ReactiveRedisIndexedSessionRepository sessionRepository;

    @EventListener
    void onExtensionInitialized(ExtensionInitializedEvent ignored) {
        apply();
    }

    @EventListener
    void onParameterSettingChanged(ParameterSettingChangedEvent event) {
        if (java.util.Objects.equals(
                event.getOldData().get("server"), event.getNewData().get("server"))) {
            return;
        }
        apply();
    }

    void apply() {
        parameterSettingGroupService.getServer().subscribe(
                server -> apply(server.getAccessTokenTtlSeconds()),
                error -> log.warn("Unable to read the access token TTL", error));
    }

    void apply(Integer accessTokenTtlSeconds) {
        var seconds = accessTokenTtlSeconds == null
                ? DEFAULT_ACCESS_TOKEN_TTL_SECONDS
                : Math.max(60, accessTokenTtlSeconds);
        sessionRepository.setDefaultMaxInactiveInterval(Duration.ofSeconds(seconds));
        log.info("Applied access token TTL of {} seconds to new sessions", seconds);
    }
}
