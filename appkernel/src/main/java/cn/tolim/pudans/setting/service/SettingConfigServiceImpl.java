package cn.tolim.pudans.setting.service;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.utils.SettingUtils;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.node.ObjectNode;


@Component
@RequiredArgsConstructor
class SettingConfigServiceImpl implements SettingConfigService {

    private final ReactiveExtensionClient client;

    @Override
    public Mono<Void> upsertConfig(String configMapName, ObjectNode configJsonData) {
        Assert.notNull(configMapName, "ConfigMap 名称不能为空");
        Assert.notNull(configJsonData, "ConfigMap 的 JSON 数据不能为空");
        var data = SettingUtils.settingConfigJsonToMap(configJsonData);
        return Mono.defer(() -> client.fetch(ConfigMap.class, configMapName).flatMap(persisted -> {
                    persisted.setData(data);
                    return client.update(persisted);
                }))
                .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                        .filter(OptimisticLockingFailureException.class::isInstance))
                .switchIfEmpty(Mono.defer(() -> {
                    var configMap = new ConfigMap();
                    configMap.setMetadata(new Metadata());
                    configMap.getMetadata().setName(configMapName);
                    configMap.setData(data);
                    return client.create(configMap);
                }))
                .then();
    }

    @Override
    public Mono<ObjectNode> fetchConfig(String configMapName) {
        return client.fetch(ConfigMap.class, configMapName).map(SettingUtils::settingConfigToJson);
    }
}
