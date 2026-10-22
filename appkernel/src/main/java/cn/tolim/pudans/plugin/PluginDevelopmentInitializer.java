package cn.tolim.pudans.plugin;

import static cn.tolim.pudans.contract.extension.MetadataUtil.nullSafeAnnotations;

import cn.tolim.pudans.contract.core.extension.Plugin;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.utils.ReactiveUtils;
import java.nio.file.Path;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginManager;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;


@Slf4j
@Component
public class PluginDevelopmentInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private static final Duration BLOCKING_TIMEOUT = ReactiveUtils.DEFAULT_TIMEOUT;

    private final PluginManager pluginManager;

    private final PluginProperties pluginProperties;

    private final ReactiveExtensionClient extensionClient;

    public PluginDevelopmentInitializer(
            PluginManager pluginManager, PluginProperties pluginProperties, ReactiveExtensionClient extensionClient) {
        this.pluginManager = pluginManager;
        this.pluginProperties = pluginProperties;
        this.extensionClient = extensionClient;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent ignored) {
        if (!pluginManager.isDevelopment()) {
            return;
        }
        createFixedPluginIfNecessary();
    }

    private void createFixedPluginIfNecessary() {
        for (Path path : pluginProperties.getFixedPluginPath()) {
            Plugin plugin = new YamlPluginFinder().find(path);
            extensionClient
                    .fetch(Plugin.class, plugin.getMetadata().getName())
                    .flatMap(persistent -> {
                        plugin.getMetadata().setVersion(persistent.getMetadata().getVersion());
                        nullSafeAnnotations(plugin).put(PluginConst.RUNTIME_MODE_ANNO, "dev");
                        return extensionClient.update(plugin);
                    })
                    .switchIfEmpty(Mono.defer(() -> {
                        nullSafeAnnotations(plugin).put(PluginConst.RUNTIME_MODE_ANNO, "dev");
                        return extensionClient.create(plugin);
                    }))
                    .retryWhen(Retry.backoff(10, Duration.ofMillis(100))
                            .filter(t -> t instanceof OptimisticLockingFailureException))
                    .block(BLOCKING_TIMEOUT);
        }
    }
}
