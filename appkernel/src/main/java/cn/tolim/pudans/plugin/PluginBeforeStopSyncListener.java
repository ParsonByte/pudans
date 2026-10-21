package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.GroupVersionKind;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.exception.SchemeNotFoundException;
import cn.tolim.pudans.contract.plugin.SpringPlugin;
import cn.tolim.pudans.plugin.event.PudansPluginBeforeStopEvent;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;


@Slf4j
@Component
class PluginBeforeStopSyncListener {

    private static final Duration CLEANUP_TIMEOUT = Duration.ofMinutes(1);

    private final ReactiveExtensionClient client;

    public PluginBeforeStopSyncListener(ReactiveExtensionClient client) {
        this.client = client;
    }

    @EventListener
    void onApplicationEvent(PudansPluginBeforeStopEvent event) {
        var pluginWrapper = event.getPlugin();
        var p = pluginWrapper.getPlugin();
        if (!(p instanceof SpringPlugin springPlugin)) {
            return;
        }
        var applicationContext = springPlugin.getApplicationContext();
        if (!(applicationContext instanceof PluginApplicationContext pluginApplicationContext)) {
            return;
        }
        cleanUpPluginExtensionResources(pluginApplicationContext).block(CLEANUP_TIMEOUT);
    }

    private Mono<Void> cleanUpPluginExtensionResources(PluginApplicationContext context) {
        var gvkExtensionNames = context.extensionNamesMapping();
        return Flux.fromIterable(gvkExtensionNames.entrySet())
                .flatMap(entry -> Flux.fromIterable(entry.getValue())
                        .flatMap(extensionName -> client.fetch(entry.getKey(), extensionName)
                                .onErrorComplete(SchemeNotFoundException.class)
                                .filter(e -> !ExtensionUtil.hasDoNotOverwriteLabel(e))
                                .flatMap(client::delete)
                                .retryWhen(Retry.backoff(10, Duration.ofMillis(100))
                                        .filter(OptimisticLockingFailureException.class::isInstance)))
                        .flatMap(e -> waitForDeleted(
                                e.groupVersionKind(), e.getMetadata().getName())))
                .then();
    }

    private Mono<Void> waitForDeleted(GroupVersionKind gvk, String name) {
        return client.fetch(gvk, name)
                .flatMap(e -> {
                    if (log.isDebugEnabled()) {
                        log.debug("Wait for {}/{} deleted", gvk, name);
                    }
                    return Mono.error(new IllegalStateException("Wait for extension deleted"));
                })
                .retryWhen(Retry.backoff(10, Duration.ofMillis(100)).filter(IllegalStateException.class::isInstance))
                .then()
                .doOnSuccess(v -> {
                    if (log.isDebugEnabled()) {
                        log.debug("{}/{} was deleted successfully.", gvk, name);
                    }
                });
    }
}
