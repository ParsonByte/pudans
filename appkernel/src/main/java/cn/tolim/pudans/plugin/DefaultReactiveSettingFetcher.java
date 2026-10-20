package cn.tolim.pudans.plugin;

import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ExtensionMatcher;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.contract.plugin.PluginConfigUpdatedEvent;
import cn.tolim.pudans.contract.plugin.PluginContext;
import cn.tolim.pudans.contract.plugin.ReactiveSettingFetcher;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import cn.tolim.pudans.infra.utils.SystemConfigUtils;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import reactor.core.publisher.Mono;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;


@Slf4j
class DefaultReactiveSettingFetcher
        implements ReactiveSettingFetcher, Reconciler<Reconciler.Request>, ApplicationContextAware {

    private static final Duration TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;

    private final JsonMapper mapper = JsonMapper.builder().build();

    private final ReactiveExtensionClient client;

    private final Mono<ConfigMap> configMapCache;

    private final AtomicBoolean isCacheInvalidated = new AtomicBoolean(false);
    private final String pluginName;
    private final String configMapName;
    private ApplicationContext applicationContext;

    public DefaultReactiveSettingFetcher(PluginContext pluginContext, ReactiveExtensionClient client) {
        this.client = client;
        this.pluginName = pluginContext.getName();
        this.configMapName = pluginContext.getConfigMapName();
        this.configMapCache = Mono.defer(() -> {
                    if (StringUtils.isBlank(configMapName)) {
                        return Mono.empty();
                    }
                    return client.fetch(ConfigMap.class, configMapName);
                })
                .cacheInvalidateIf(cm -> isCacheInvalidated.getAndSet(false));
    }

    static String buildCacheKey(String pluginName) {
        return "plugin-" + pluginName + "-configmap";
    }

    @Override
    public <T> Mono<T> fetch(String group, Class<T> clazz) {
        return getSettingValue(group).map(n -> mapper.convertValue(n, clazz));
    }

    @Override
    public Mono<tools.jackson.databind.JsonNode> getSettingValue(String group) {
        return getSettingValues().mapNotNull(m -> m.get(group));
    }

    @Override
    public Mono<Map<String, tools.jackson.databind.JsonNode>> getSettingValues() {
        return configMapCache
                .mapNotNull(ConfigMap::getData)
                .map(this::toJackson3JsonNodeMap)
                .defaultIfEmpty(Map.of());
    }

    private Map<String, tools.jackson.databind.JsonNode> toJackson3JsonNodeMap(@Nullable Map<String, String> data) {
        if (data == null) {
            return Map.of();
        }
        return data.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> toJackson3JsonNode(e.getValue())));
    }

    private tools.jackson.databind.JsonNode toJackson3JsonNode(@Nullable String json) {
        if (StringUtils.isBlank(json)) {
            return mapper.missingNode();
        }
        try {
            return mapper.readTree(json);
        } catch (JacksonException ex) {
            log.error("Failed to parse plugin [{}] config json: [{}]", pluginName, json, ex);
            return mapper.missingNode();
        }
    }

    @Override
    public Result reconcile(Request request) {
        return client.fetch(ConfigMap.class, configMapName)
                .filter(Predicate.not(ExtensionUtil::isDeleted))
                .flatMap(cm -> {

                    var snapshot = SystemConfigUtils.getDataSnapshot(cm);
                    if (SystemConfigUtils.populateChecksum(cm)) {

                        SystemConfigUtils.updateDataSnapshot(cm);
                        return client.update(cm).then(Mono.fromCallable(() -> {
                            this.isCacheInvalidated.set(true);
                            applicationContext.publishEvent(PluginConfigUpdatedEvent.builder()
                                    .source(this)
                                    .oldSettingValues(toJackson3JsonNodeMap(snapshot))
                                    .newSettingValues(toJackson3JsonNodeMap(cm.getData()))
                                    .build());
                            return null;
                        }));
                    }
                    return Mono.<Result>empty();
                })
                .blockOptional(TIMEOUT)
                .orElse(null);
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        if (StringUtils.isBlank(configMapName)) {

            return builder.extension(new ConfigMap())
                    .syncAllOnStart(false)
                    .onAddMatcher(extension -> false)
                    .onUpdateMatcher(extension -> false)
                    .onDeleteMatcher(extension -> false)
                    .build();
        }
        ExtensionMatcher matcher =
                extension -> Objects.equals(extension.getMetadata().getName(), configMapName);
        return builder.extension(new ConfigMap())
                .syncAllOnStart(true)
                .syncAllListOptions(ListOptions.builder()
                        .fieldQuery(equal("metadata.name", configMapName))
                        .build())
                .onAddMatcher(matcher)
                .onUpdateMatcher(matcher)
                .onDeleteMatcher(matcher)
                .build();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
