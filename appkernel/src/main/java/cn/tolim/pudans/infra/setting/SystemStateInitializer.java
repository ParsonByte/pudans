package cn.tolim.pudans.infra.setting;

import static cn.tolim.pudans.contract.extension.index.query.Queries.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import cn.tolim.pudans.contract.core.extension.user.User;
import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.PageRequestImpl;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.extension.router.selector.FieldSelector;
import cn.tolim.pudans.contract.extension.router.selector.LabelSelector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Slf4j
@Component
@RequiredArgsConstructor
class SystemStateInitializer {

    private final ReactiveExtensionClient client;

    @EventListener
    Mono<Void> onApplicationEvent(ExtensionInitializedEvent ignored) {
        return client.fetch(ConfigMap.class, SystemState.SYSTEM_STATES_CONFIGMAP)
                .map(configMap -> isTrue(SystemState.deserialize(configMap).getIsSetup()))
                .defaultIfEmpty(false)
                .flatMap(initialized -> initialized ? Mono.<Void>empty() : backfillLegacyInstallation())
                .onErrorResume(error -> {
                    log.error("回填系统状态失败，启动将继续。", error);
                    return Mono.empty();
                })
                .then();
    }

    private Mono<Void> backfillLegacyInstallation() {
        return hasUser()
                .filter(Boolean::booleanValue)
                .flatMap(ignored -> SystemState.upsetSystemState(client, systemState -> {

                    if (systemState.getIsSetup() == null) {
                        systemState.setIsSetup(true);
                        log.info("已为旧版安装回填系统状态。");
                    }
                }))
                .then();
    }

    private Mono<Boolean> hasUser() {
        var listOptions = new ListOptions();
        listOptions.setLabelSelector(
                LabelSelector.builder().notEq(User.HIDDEN_USER_LABEL, "true").build());
        listOptions.setFieldSelector(FieldSelector.of(isNull("metadata.deletionTimestamp")));
        return client.listBy(User.class, listOptions, PageRequestImpl.ofSize(1))
                .map(result -> result.getTotal() > 0)
                .defaultIfEmpty(false);
    }
}
