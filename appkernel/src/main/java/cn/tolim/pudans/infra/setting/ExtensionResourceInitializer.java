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
import cn.tolim.pudans.contract.rbac.Role;
import cn.tolim.pudans.extension.contract.ExtensionUtil;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.Unstructured;
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

    // 注入扩展客户端与属性，用于加载初始资源。
    public ExtensionResourceInitializer(
            PudansProperties pudansProperties,
            InternalReactiveExtensionClient extensionClient,
            ApplicationEventPublisher eventPublisher) {
        this.pudansProperties = pudansProperties;
        this.extensionClient = extensionClient;
        this.eventPublisher = eventPublisher;
    }

    // 加载扩展文件并创建或更新，随后清理过期模板。
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

        var roleTemplates = new HashSet<String>();
        Flux.fromIterable(locations)
                .doOnNext(location -> log.debug("尝试从位置 {} 初始化扩展资源", location))
                .map(this::listResources)
                .distinct()
                .flatMapIterable(resources -> resources)
                .doOnNext(resource -> log.debug("正在从位置 {} 初始化扩展资源", resource))
                .map(resource -> new YamlUnstructuredLoader(resource).load())
                .flatMapIterable(extensions -> extensions)
                .doOnNext(extension -> {
                    if (isRoleTemplate(extension)) {
                        roleTemplates.add(extension.getMetadata().getName());
                    }
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
                .then(Mono.fromRunnable(() -> pruneRoleTemplates(roleTemplates)))
                .block(Duration.ofMinutes(1));
        eventPublisher.publishEvent(new ExtensionInitializedEvent(this));
    }

    // 标记初始化器已停止，避免重复启动。
    @Override
    public void stop() {
        if (!running) {
            return;
        }
        running = false;
    }

    // 返回初始化器是否处于运行状态。
    @Override
    public boolean isRunning() {
        return running;
    }

    // 在控制器就绪前完成扩展资源初始化。
    @Override
    public int getPhase() {
        return InitializationPhase.EXTENSION_RESOURCES.getPhase();
    }

    // 判断资源是否为带模板标签的角色定义。
    private static boolean isRoleTemplate(Unstructured extension) {
        var metadata = extension.getMetadata();
        var labels = metadata == null ? null : metadata.getLabels();
        return Role.KIND.equals(extension.getKind())
                && labels != null
                && Boolean.parseBoolean(labels.get(Role.TEMPLATE_LABEL_NAME));
    }

    // 删除代码中已移除的角色模板，避免残留。
    private void pruneRoleTemplates(Set<String> loadedTemplates) {
        var options = ListOptions.builder()
                .labelSelector()
                .eq(Role.TEMPLATE_LABEL_NAME, Boolean.TRUE.toString())
                .end()
                .build();
        var stale = extensionClient
                .listAll(Role.class, options, ExtensionUtil.defaultSort())
                .filter(role -> role.getMetadata() != null
                        && !loadedTemplates.contains(role.getMetadata().getName()))
                .collectList()
                .block(Duration.ofSeconds(30));
        if (CollectionUtils.isEmpty(stale)) {
            return;
        }
        stale.forEach(role -> {
            log.info("删除已从代码中移除的角色模板：{}", role.getMetadata().getName());
            extensionClient.delete(role).block(Duration.ofSeconds(30));
        });
    }

    // 已存在则更新，不存在且未删除时新建。
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

    // 解析类路径位置为资源列表，非法位置直接报错。
    private List<Resource> listResources(String location) {
        var resolver = new PathMatchingResourcePatternResolver();
        try {
            return List.of(resolver.getResources(location));
        } catch (IOException ie) {
            throw new IllegalArgumentException("扩展位置无效：" + location, ie);
        }
    }
}
