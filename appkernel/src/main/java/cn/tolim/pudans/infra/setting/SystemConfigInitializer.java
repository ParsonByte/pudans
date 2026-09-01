package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.SystemSetting;
import java.util.HashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
class SystemConfigInitializer {

    private final ReactiveExtensionClient client;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    Mono<Void> onApplicationEvent(ExtensionInitializedEvent ignored) {
        return client.fetch(ConfigMap.class, SystemSetting.SYSTEM_CONFIG)
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Initializing system config...");
                    var configMap = new ConfigMap();
                    configMap.setMetadata(new Metadata());
                    configMap.getMetadata().setName(SystemSetting.SYSTEM_CONFIG);
                    configMap.setData(new HashMap<>());
                    return client.create(configMap).doOnSuccess(created -> {
                        log.info(
                                "System config initialized: {}",
                                created.getMetadata().getName());
                    });
                }))
                .then();
    }
}
