package cn.tolim.pudans.plugin.extensionpoint;

import cn.tolim.pudans.contract.infra.SystemSetting.ExtensionPointEnabled;
import cn.tolim.pudans.contract.plugin.SpringPlugin;
import cn.tolim.pudans.contract.plugin.SpringPluginManager;
import cn.tolim.pudans.contract.plugin.extensionpoint.ExtensionGetter;
import cn.tolim.pudans.infra.setting.SystemConfigFetcher;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.ExtensionPoint;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultExtensionGetter implements ExtensionGetter {

    private final SystemConfigFetcher systemConfigFetcher;

    private final SpringPluginManager pluginManager;

    private final BeanFactory beanFactory;

    private final ExtensionDefinitionGetter extensionDefinitionGetter;

    private final ExtensionPointDefinitionGetter extensionPointDefinitionGetter;

    @Override
    public <T extends ExtensionPoint> Flux<T> getExtensions(Class<T> extensionPoint) {
        return Flux.fromIterable(lookExtensions(extensionPoint))
                .concatWith(Flux.fromStream(
                        () -> beanFactory.getBeanProvider(extensionPoint).orderedStream()))
                .sort(new AnnotationAwareOrderComparator());
    }

    @Override
    public <T extends ExtensionPoint> List<T> getExtensionList(Class<T> extensionPoint) {
        var extensions = new LinkedList<T>();
        extensions.addAll(lookExtensions(extensionPoint));
        extensions.addAll(
                beanFactory.getBeanProvider(extensionPoint).orderedStream().toList());
        extensions.sort(new AnnotationAwareOrderComparator());
        return extensions;
    }

    @Override
    public <T extends ExtensionPoint> Mono<T> getEnabledExtension(Class<T> extensionPoint) {
        return getEnabledExtensions(extensionPoint).next();
    }

    @Override
    public <T extends ExtensionPoint> Flux<T> getEnabledExtensions(Class<T> extensionPoint) {
        return fetchExtensionPointDefinition(extensionPoint).flatMapMany(epd -> {
            var epdName = epd.getMetadata().getName();
            var type = epd.getSpec().getType();
            if (type == ExtensionPointDefinition.ExtensionPointType.SINGLETON) {
                return getEnabledExtensions(epdName, extensionPoint).take(1);
            }

            return getEnabledExtensions(epdName, extensionPoint);
        });
    }

    private <T extends ExtensionPoint> Flux<T> getEnabledExtensions(String epdName, Class<T> extensionPoint) {
        return systemConfigFetcher
                .fetch(ExtensionPointEnabled.GROUP, ExtensionPointEnabled.class)
                .switchIfEmpty(Mono.fromSupplier(ExtensionPointEnabled::new))
                .flatMapMany(enabled -> {
                    var extensionDefNames = enabled.getOrDefault(epdName, null);
                    if (extensionDefNames == null) {

                        return Flux.defer(() -> getExtensions(extensionPoint));
                    }
                    var extensions = getExtensions(extensionPoint).cache();
                    return Flux.fromIterable(extensionDefNames)
                            .flatMapSequential(extensionDefinitionGetter::get)
                            .flatMapSequential(extensionDef -> {
                                var className = extensionDef.getSpec().getClassName();
                                return extensions.filter(extension ->
                                        Objects.equals(extension.getClass().getName(), className));
                            });
                });
    }

    private Mono<ExtensionPointDefinition> fetchExtensionPointDefinition(
            Class<? extends ExtensionPoint> extensionPoint) {
        return extensionPointDefinitionGetter.getByClassName(extensionPoint.getName());
    }

    protected <T> List<T> lookExtensions(Class<T> type) {
        List<T> beans = new ArrayList<>();

        var startedPlugins = pluginManager.startedPlugins();
        for (PluginWrapper startedPlugin : startedPlugins) {
            if (startedPlugin.getPlugin() instanceof SpringPlugin springPlugin) {
                var pluginApplicationContext = springPlugin.getApplicationContext();
                try {
                    pluginApplicationContext.getBeansOfType(type).forEach((name, bean) -> beans.add(bean));
                } catch (Throwable e) {

                    log.error("Error while looking for extensions of type {}", type, e);
                }
            } else {
                var extensions = pluginManager.getExtensions(type, startedPlugin.getPluginId());
                beans.addAll(extensions);
            }
        }
        return beans;
    }
}
