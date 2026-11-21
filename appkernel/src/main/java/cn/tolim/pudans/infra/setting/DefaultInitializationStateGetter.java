package cn.tolim.pudans.infra.setting;

import static cn.tolim.pudans.extension.contract.index.query.Queries.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.PageRequestImpl;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.router.selector.FieldSelector;
import cn.tolim.pudans.extension.contract.router.selector.LabelSelector;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class DefaultInitializationStateGetter implements InitializationStateGetter {
    private final ReactiveExtensionClient client;
    private final AtomicBoolean setupCompleted = new AtomicBoolean(false);

    // 先读系统状态；缺失时根据用户数量推断是否需初始化。
    @Override
    public Mono<Boolean> setupRequired() {
        if (setupCompleted.get()) {
            return Mono.just(false);
        }
        return client.fetch(ConfigMap.class, SystemState.SYSTEM_STATES_CONFIGMAP)
                .mapNotNull(configMap -> SystemState.deserialize(configMap).getIsSetup())
                .map(initialized -> {
                    var setupRequired = !isTrue(initialized);
                    if (!setupRequired) {

                        setupCompleted.set(true);
                    }
                    return setupRequired;
                })
                .switchIfEmpty(Mono.defer(this::setupRequiredWithoutState));
    }

    // 没有任何有效用户时视为仍需完成初始化。
    private Mono<Boolean> setupRequiredWithoutState() {
        var listOptions = new ListOptions();
        listOptions.setLabelSelector(
                LabelSelector.builder().notEq(User.HIDDEN_USER_LABEL, "true").build());
        listOptions.setFieldSelector(FieldSelector.of(isNull("metadata.deletionTimestamp")));
        return client.listBy(User.class, listOptions, PageRequestImpl.ofSize(1)).map(result -> result.getTotal() == 0);
    }
}
