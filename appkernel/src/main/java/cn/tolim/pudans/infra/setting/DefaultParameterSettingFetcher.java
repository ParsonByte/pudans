package cn.tolim.pudans.infra.setting;

import static cn.tolim.pudans.infra.contract.ParameterSetting.PARAMETER_SETTINGS;
import static cn.tolim.pudans.infra.contract.ParameterSetting.PARAMETER_SETTINGS_DEFAULT;

import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.contract.utils.JsonParseException;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import cn.tolim.pudans.infra.utils.ParameterSettingUtils;
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
class DefaultParameterSettingFetcher
        implements ParameterSettingFetcher, ApplicationListener<ParameterSettingChangedEvent> {

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
                return computeParameterSettings()
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
    public void onApplicationEvent(ParameterSettingChangedEvent event) {
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
    public Mono<ParameterSetting.Basic> getBasic() {
        return fetch(ParameterSetting.Basic.GROUP, ParameterSetting.Basic.class)
                .switchIfEmpty(Mono.fromSupplier(ParameterSetting.Basic::new));
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
        return extensionClient.fetch(ConfigMap.class, PARAMETER_SETTINGS);
    }


    @Override
    public Optional<ConfigMap> getConfigMapBlocking() {
        return getConfigMap().blockOptional(BLOCKING_TIMEOUT);
    }

    private Mono<ConfigMap> computeParameterSettings() {
        var getOverrideConfigMap = extensionClient.fetch(ConfigMap.class, PARAMETER_SETTINGS);
        var getDefaultConfigMap = extensionClient
                .fetch(ConfigMap.class, PARAMETER_SETTINGS_DEFAULT)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    var defaultConfigMap = new ConfigMap();
                    defaultConfigMap.setData(Map.of());
                    return defaultConfigMap;
                }));
        return Mono.zip(getDefaultConfigMap, getOverrideConfigMap, (defaultConfigMap, overrideConfigMap) -> {
            try {
                return ParameterSettingUtils.mergeConfigMap(defaultConfigMap, overrideConfigMap);
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
