package cn.tolim.pudans.infra.setting;

import static cn.tolim.pudans.extension.api.index.query.Queries.isNull;
import static org.apache.commons.lang3.BooleanUtils.isTrue;

import cn.tolim.pudans.contract.user.User;
import cn.tolim.pudans.extension.api.ConfigMap;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.PageRequestImpl;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.extension.api.router.selector.FieldSelector;
import cn.tolim.pudans.extension.api.router.selector.LabelSelector;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;


@Component
@RequiredArgsConstructor
public class DefaultInitializationStateGetter implements InitializationStateGetter {
    private final ReactiveExtensionClient client;
    private final AtomicBoolean setupCompleted = new AtomicBoolean(false);

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

    private Mono<Boolean> setupRequiredWithoutState() {
        var listOptions = new ListOptions();
        listOptions.setLabelSelector(
                LabelSelector.builder().notEq(User.HIDDEN_USER_LABEL, "true").build());
        listOptions.setFieldSelector(FieldSelector.of(isNull("metadata.deletionTimestamp")));
        return client.listBy(User.class, listOptions, PageRequestImpl.ofSize(1)).map(result -> result.getTotal() == 0);
    }
}
