package cn.tolim.pudans.core.infra.setting.reconciler;

import static cn.tolim.pudans.contract.extension.index.query.Queries.equal;
import static cn.tolim.pudans.core.infra.utils.ParameterSettingUtils.mergeMap;
import static java.util.Objects.requireNonNullElse;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.ExtensionMatcher;
import cn.tolim.pudans.contract.extension.ExtensionUtil;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.infra.setting.ParameterSettingChangedEvent;
import cn.tolim.pudans.core.infra.utils.ParameterSettingUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;

@Slf4j
@Component
@RequiredArgsConstructor
class ParameterSettingReconciler implements Reconciler<Reconciler.Request> {

    private final ExtensionClient client;

    private final ApplicationEventPublisher eventPublisher;

    @Override
    public Result reconcile(Request request) {
        Assert.state(
                Objects.equals(ParameterSetting.PARAMETER_SETTINGS, request.name()),
                "仅支持通过系统配置协调器协调系统配置。");
        client.fetch(ConfigMap.class, request.name()).ifPresent(configMap -> {
            if (ExtensionUtil.isDeleted(configMap)) {
                log.warn("有人尝试删除参数设置 ConfigMap");
                return;
            }


            var dataSnapshot = ParameterSettingUtils.getDataSnapshot(configMap);
            if (ParameterSettingUtils.populateChecksum(configMap)) {
                ParameterSettingUtils.updateDataSnapshot(configMap);
                client.update(configMap);
                log.info("检测到参数设置已变更");
                eventPublisher.publishEvent(computeChangedEvent(configMap, dataSnapshot));
            }

        });
        return null;
    }

    @Override
    public Controller setupWith(ControllerBuilder builder) {
        ExtensionMatcher matcher =
                extension -> Objects.equals(extension.getMetadata().getName(), ParameterSetting.PARAMETER_SETTINGS);
        return builder.extension(new ConfigMap())
                .syncAllOnStart(true)
                .syncAllListOptions(ListOptions.builder()
                        .fieldQuery(equal("metadata.name", ParameterSetting.PARAMETER_SETTINGS))
                        .build())
                .onAddMatcher(matcher)
                .onUpdateMatcher(matcher)
                .onDeleteMatcher(matcher)
                .build();
    }

    private ParameterSettingChangedEvent computeChangedEvent(
            ConfigMap configMap, @Nullable Map<String, String> oldData) {
        return client.fetch(ConfigMap.class, ParameterSetting.PARAMETER_SETTINGS_DEFAULT)
                .map(defaultConfigMap -> {
                    var defaultData = requireNonNullElse(defaultConfigMap.getData(), Map.<String, String>of());
                    try {
                        var mergedOldData = mergeMap(defaultData, requireNonNullElse(oldData, Map.of()));
                        var mergedNewData = mergeMap(defaultData, requireNonNullElse(configMap.getData(), Map.of()));
                        return new ParameterSettingChangedEvent(this, mergedOldData, mergedNewData);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElseGet(() -> new ParameterSettingChangedEvent(
                        this, oldData, requireNonNullElse(configMap.getData(), Map.of())));
    }
}
