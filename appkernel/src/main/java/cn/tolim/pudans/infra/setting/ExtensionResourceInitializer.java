package cn.tolim.pudans.infra.setting;

import java.io.IOException;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import cn.tolim.pudans.extension.api.ExtensionUtil;
import cn.tolim.pudans.extension.api.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.api.Unstructured;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;

@Slf4j
@Component
public class ExtensionResourceInitializer implements SmartLifecycle {

    private volatile boolean running;

    public static final Set<String> REQUIRED_EXTENSION_LOCATIONS = Set.of(
            "classpath:/extensions/*.yaml",
            "classpath:/extensions/*.yml",
            "classpath:/extensions/**/*.yaml",
            "classpath:/extensions/**/*.yml");
    private final PudansProperties pudansProperties;
    private final InternalReactiveExtensionClient extensionClient;

    private final ApplicationEventPublisher eventPublisher;

    public ExtensionResourceInitializer(
            PudansProperties pudansProperties,
            InternalReactiveExtensionClient extensionClient,
            ApplicationEventPublisher eventPublisher) {
        this.pudansProperties = pudansProperties;
        this.extensionClient = extensionClient;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        var locations = new HashSet<String>();
        if (!pudansProperties.isRequiredExtensionDisabled()) {
            locations.addAll(REQUIRED_EXTENSION_LOCATIONS);
        }
        if (pudansProperties.getInitialExtensionLocations() != null) {
            locations.addAll(pudansProperties.getInitialExtensionLocations());
        }
        if (CollectionUtils.isEmpty(locations)) {
            return;
        }

        Flux.fromIterable(locations)
                .doOnNext(location -> log.debug("尝试从位置 {} 初始化扩展资源", location))
                .map(this::listResources)
                .distinct()
                .flatMapIterable(resources -> resources)
                .doOnNext(resource -> log.debug("正在从位置 {} 初始化扩展资源", resource))
                .map(resource -> new YamlUnstructuredLoader(resource).load())
                .flatMapIterable(extensions -> extensions)
                .doOnNext(extension -> {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "正在初始化扩展资源：{}/{}",
                                extension.groupVersionKind(),
                                extension.getMetadata().getName());
                    }
                })
                .flatMap(this::createOrUpdate)
                .doOnNext(extension -> {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "已初始化扩展资源：{}/{}",
                                extension.groupVersionKind(),
                                extension.getMetadata().getName());
                    }
                })
                .then()
                .block(Duration.ofMinutes(1));
        eventPublisher.publishEvent(new ExtensionInitializedEvent(this));
    }

    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    @Override
    public int getPhase() {
        return InitializationPhase.EXTENSION_RESOURCES.getPhase();
    }

    private Mono<Unstructured> createOrUpdate(Unstructured extension) {
        return Mono.just(extension)
                .flatMap(ext -> extensionClient.fetch(
                        extension.groupVersionKind(), extension.getMetadata().getName()))
                .flatMap(existingExt -> {
                    if (ExtensionUtil.hasDoNotOverwriteLabel(existingExt)) {
                        log.debug(
                                "扩展 {} 标记为不覆盖，跳过更新",
                                existingExt.getMetadata().getName());
                        
                        return Mono.just(existingExt);
                    }
                    
                    extension.getMetadata().setVersion(existingExt.getMetadata().getVersion());
                    return extensionClient.updateInternal(extension);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    if (ExtensionUtil.isDeleted(extension)) {
                        
                        return Mono.empty();
                    }
                    return extensionClient.createInternal(extension);
                }));
    }

    private List<Resource> listResources(String location) {
        var resolver = new PathMatchingResourcePatternResolver();
        try {
            return List.of(resolver.getResources(location));
        } catch (IOException ie) {
            throw new IllegalArgumentException("扩展位置无效：" + location, ie);
        }
    }
}
