package cn.tolim.pudans.core.user;

import cn.tolim.pudans.extension.api.ConfigMap;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Objects;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementReads {

    private static final String CONFIGMAP_PREFIX = "announcement-state-";

    private static final String STATE_KEY = "state";

    private final ParameterGroups parameterGroups;

    private final ReactiveExtensionClient client;

    private Clock clock = Clock.systemUTC();

    void setClock(Clock clock) {
        this.clock = clock;
    }

    public Mono<AnnouncementState> get(String username) {
        return parameterGroups.getSite().flatMap(site -> {
            var version = site.getAnnouncementVersion();
            return client.fetch(ConfigMap.class, configMapName(username))
                    .map(this::readState)
                    .defaultIfEmpty(new StoredState())
                    .map(state -> stateFor(version, state));
        });
    }

    public Mono<AnnouncementState> dismiss(String username) {
        return parameterGroups.getSite().flatMap(site -> {
            var version = site.getAnnouncementVersion();
            var dismissedAt = clock.instant();
            return client.fetch(ConfigMap.class, configMapName(username))
                    .flatMap(configMap -> saveState(configMap, version, dismissedAt))
                    .switchIfEmpty(Mono.defer(() -> {
                        var created = newConfigMap(username);
                        putState(created, version, dismissedAt);
                        return client.create(created);
                    }))
                    .map(ignored -> new AnnouncementState(version, dismissedAt, StringUtils.hasText(version)));
        });
    }

    private Mono<ConfigMap> saveState(ConfigMap configMap, String version, Instant dismissedAt) {
        putState(configMap, version, dismissedAt);
        return client.update(configMap);
    }

    private static void putState(ConfigMap configMap, String version, Instant dismissedAt) {
        var state = new StoredState();
        state.setVersion(version);
        state.setDismissedAt(dismissedAt);
        if (configMap.getData() == null) {
            configMap.setData(new HashMap<>());
        }
        configMap.getData().put(STATE_KEY, JsonUtils.objectToJson(state));
    }

    public Mono<Void> deleteForUser(String username) {
        return client.fetch(ConfigMap.class, configMapName(username))
                .flatMap(client::delete)
                .then();
    }

    private static AnnouncementState stateFor(String version, StoredState state) {
        var dismissed = StringUtils.hasText(version)
                && Objects.equals(version, state.getVersion())
                && state.getDismissedAt() != null;
        return new AnnouncementState(version, dismissed ? state.getDismissedAt() : null, dismissed);
    }

    private StoredState readState(ConfigMap configMap) {
        if (configMap.getData() == null || !configMap.getData().containsKey(STATE_KEY)) {
            return new StoredState();
        }
        try {
            return JsonUtils.jsonToObject(configMap.getData().get(STATE_KEY), StoredState.class);
        } catch (RuntimeException e) {
            log.warn("忽略 [{}] 的格式错误的公告状态", configMap.getMetadata().getName());
            return new StoredState();
        }
    }

    private static ConfigMap newConfigMap(String username) {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName(configMapName(username));
        configMap.setMetadata(metadata);
        configMap.setData(new HashMap<>());
        return configMap;
    }

    private static String configMapName(String username) {
        return CONFIGMAP_PREFIX + username;
    }

    @Data
    static class StoredState {
        private String version;
        private Instant dismissedAt;
    }

    public record AnnouncementState(String version, Instant dismissedAt, boolean dismissed) {}
}
