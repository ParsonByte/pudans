package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.plugin.SharedEvent;
import cn.tolim.pudans.contract.plugin.SpringPlugin;
import cn.tolim.pudans.contract.plugin.SpringPluginManager;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.Lifecycle;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SharedEventDispatcher {

    private final SpringPluginManager pluginManager;

    private final ApplicationEventPublisher publisher;

    @EventListener(ApplicationEvent.class)
    void onApplicationEvent(ApplicationEvent event) {
        if (AnnotationUtils.findAnnotation(event.getClass(), SharedEvent.class) == null) {
            return;
        }

        var startedPlugins = pluginManager.startedPlugins();

        for (var startedPlugin : startedPlugins) {
            var plugin = startedPlugin.getPlugin();
            if (!(plugin instanceof SpringPlugin springPlugin)) {
                continue;
            }
            var context = springPlugin.getApplicationContext();

            if (context instanceof Lifecycle lifecycle && lifecycle.isRunning()) {
                context.publishEvent(new PudansSharedEventDelegator(this, event));
            }
        }
    }

    @EventListener(PluginSharedEventDelegator.class)
    void onApplicationEvent(PluginSharedEventDelegator event) {
        publisher.publishEvent(event.getDelegate());
    }
}
