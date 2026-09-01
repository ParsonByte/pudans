package cn.tolim.pudans.infra.setting;

import static cn.tolim.pudans.contract.infra.SystemSetting.SYSTEM_CONFIG;
import static cn.tolim.pudans.contract.infra.SystemSetting.SYSTEM_CONFIG_DEFAULT;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.SystemSetting;
import cn.tolim.pudans.contract.infra.utils.JsonParseException;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import cn.tolim.pudans.infra.utils.SystemConfigUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.ConversionService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
class DefaultSystemConfigFetcher implements SystemConfigFetcher, ApplicationListener<SystemConfigChangedEvent> {

    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;

    private final JsonMapper mapper;

    private final ReactiveExtensionClient extensionClient;

    private final ConversionService conversionService;

    private final AtomicReference<Map<String, String>> configMapCache = new AtomicReference<>();

    private final Mono<Map<String, String>> configMapMono = Mono.defer(() -> {
                var currentValue = configMapCache.get();
                if (currentValue != null) {
                    return Mono.just(currentValue);
                }
                return computeSystemConfig()
                        .mapNotNull(configMap -> {
                            if (configMapCache.compareAndSet(null, configMap.getData())) {
                                return configMap.getData();
                            } else {
                                return configMapCache.get();
                            }
                        })
                        .defaultIfEmpty(Map.of());
            })
            .cacheInvalidateIf(configMap -> {
                var currentValue = configMapCache.get();
                return currentValue == null || !currentValue.equals(configMap);
            });

    @Override
    public void onApplicationEvent(SystemConfigChangedEvent event) {
        configMapCache.set(event.getNewData());
    }

    @Override
    public <T> Mono<T> fetch(String key, Class<T> type) {
        return getValuesInternal()
                .filter(map -> map.containsKey(key))
                .map(map -> map.get(key))
                .mapNotNull(stringValue -> {
                    if (conversionService.canConvert(String.class, type)) {
                        return conversionService.convert(stringValue, type);
                    }
                    return mapper.readValue(stringValue, type);
                });
    }

    @Override
    public Mono<SystemSetting.Basic> getBasic() {
        return fetch(SystemSetting.Basic.GROUP, SystemSetting.Basic.class)
                .switchIfEmpty(Mono.fromSupplier(SystemSetting.Basic::new));
    }

    private Mono<Map<String, String>> getValuesInternal() {
        return configMapMono;
    }

    @Override
    public Mono<Map<String, String>> getConfig() {
        return configMapMono;
    }


    @Override
    public Mono<ConfigMap> getConfigMap() {
        return extensionClient.fetch(ConfigMap.class, SYSTEM_CONFIG);
    }


    @Override
    public Optional<ConfigMap> getConfigMapBlocking() {
        return getConfigMap().blockOptional(BLOCKING_TIMEOUT);
    }

    private Mono<ConfigMap> computeSystemConfig() {
        var getOverrideConfigMap = extensionClient.fetch(ConfigMap.class, SYSTEM_CONFIG);
        var getDefaultConfigMap = extensionClient
                .fetch(ConfigMap.class, SYSTEM_CONFIG_DEFAULT)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var defaultConfigMap = new ConfigMap();
                    defaultConfigMap.setData(Map.of());
                    return defaultConfigMap;
                }));
        return Mono.zip(getDefaultConfigMap, getOverrideConfigMap, (defaultConfigMap, overrideConfigMap) -> {
            try {
                return SystemConfigUtils.mergeConfigMap(defaultConfigMap, overrideConfigMap);
            } catch (JsonProcessingException e) {
                throw new JsonParseException(e);
            }
        });
    }


    AtomicReference<Map<String, String>> getConfigMapCache() {
        return configMapCache;
    }


    Mono<Map<String, String>> getConfigMapMono() {
        return configMapMono;
    }
}
