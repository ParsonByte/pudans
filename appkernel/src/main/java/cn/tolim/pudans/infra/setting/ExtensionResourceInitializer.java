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
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.InternalReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.Unstructured;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.utils.YamlUnstructuredLoader;

@Slf4j
@Component
public class ExtensionResourceInitializer implements SmartLifecycle {

    private volatile boolean running;

    public static final Set<String> REQUIRED_EXTENSION_LOCATIONS =
            Set.of("classpath:/extensions/*.yaml", "classpath:/extensions/*.yml");
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
                .doOnNext(location -> log.debug("Trying to initialize extension resources from location: {}", location))
                .map(this::listResources)
                .distinct()
                .flatMapIterable(resources -> resources)
                .doOnNext(resource -> log.debug("Initializing extension resource from location: {}", resource))
                .map(resource -> new YamlUnstructuredLoader(resource).load())
                .flatMapIterable(extensions -> extensions)
                .doOnNext(extension -> {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Initializing extension resource: {}/{}",
                                extension.groupVersionKind(),
                                extension.getMetadata().getName());
                    }
                })
                .flatMap(this::createOrUpdate)
                .doOnNext(extension -> {
                    if (log.isDebugEnabled()) {
                        log.debug(
                                "Initialized extension resource: {}/{}",
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
                                "Extension {} is marked as do-not-overwrite, skipping update",
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
            throw new IllegalArgumentException("Invalid extension location: " + location, ie);
        }
    }
}
