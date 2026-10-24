package cn.tolim.pudans.plugin;

import static cn.tolim.pudans.plugin.PluginConst.PLUGIN_NAME_LABEL_NAME;
import static cn.tolim.pudans.plugin.PluginExtensionLoaderUtils.isSetting;
import static cn.tolim.pudans.plugin.PluginExtensionLoaderUtils.lookupExtensions;

import cn.tolim.pudans.contract.core.extension.Plugin;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.contract.plugin.SpringPlugin;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;
import cn.tolim.pudans.plugin.event.PudansPluginStartedEvent;
import java.time.Duration;
import java.util.HashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Component
class PluginStartedListener {

    private static final Duration TIMEOUT = Duration.ofMinutes(1);

    private final ReactiveExtensionClient client;

    public PluginStartedListener(ReactiveExtensionClient extensionClient) {
        this.client = extensionClient;
    }

    private Mono<Unstructured> createOrUpdate(Unstructured unstructured) {
        var name = unstructured.getMetadata().getName();
        return client.fetch(unstructured.groupVersionKind(), name)
                .doOnNext(old ->
                        unstructured.getMetadata().setVersion(old.getMetadata().getVersion()))
                .map(ignored -> unstructured)
                .flatMap(extension -> {
                    if (ExtensionUtil.hasDoNotOverwriteLabel(extension)) {
                        log.debug(
                                "Skip updating extension {} due to do-not-overwrite label",
                                extension.getMetadata().getName());
                        return Mono.just(extension);
                    }
                    return client.update(extension);
                })
                .switchIfEmpty(Mono.defer(() -> client.create(unstructured)));
    }

    @EventListener
    void onApplicationEvent(PudansPluginStartedEvent event) {
        var pluginWrapper = event.getPlugin();
        var p = pluginWrapper.getPlugin();
        if (!(p instanceof SpringPlugin springPlugin)) {
            return;
        }
        var applicationContext = springPlugin.getApplicationContext();
        if (!(applicationContext instanceof PluginApplicationContext pluginApplicationContext)) {
            return;
        }
        var pluginName = pluginWrapper.getPluginId();

        client.get(Plugin.class, pluginName)
                .flatMap(plugin -> Flux.fromStream(() -> {
                            log.debug("Collecting extensions for plugin {}", pluginName);
                            var resources = lookupExtensions(pluginWrapper.getPluginClassLoader());
                            var loader = new YamlUnstructuredLoader(resources);
                            var settingName = plugin.getSpec().getSettingName();

                            return loader.load().stream()
                                    .filter(isSetting(settingName).negate());
                        })
                        .doOnNext(unstructured -> {
                            var name = unstructured.getMetadata().getName();
                            pluginApplicationContext.addExtensionMapping(unstructured.groupVersionKind(), name);
                            var labels = unstructured.getMetadata().getLabels();
                            if (labels == null) {
                                labels = new HashMap<>();
                                unstructured.getMetadata().setLabels(labels);
                            }
                            labels.put(
                                    PLUGIN_NAME_LABEL_NAME, plugin.getMetadata().getName());
                        })
                        .flatMap(this::createOrUpdate)
                        .then())
                .block(TIMEOUT);
    }
}
