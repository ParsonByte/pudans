package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import java.time.Duration;
import java.util.HashMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Slf4j
@Component
@RequiredArgsConstructor
class ParameterSettingSiteIdInitializer {

    private final ParameterSettingFetcher parameterSettingFetcher;

    private final ReactiveExtensionClient client;

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    Mono<Void> onApplicationEvent(ExtensionInitializedEvent ignored) {
        return parameterSettingFetcher
                .getConfigMap()
                .flatMap(configMap -> {
                    if (configMap.getData() == null) {
                        configMap.setData(new HashMap<>());
                    }
                    var server = readServer(configMap.getData().get(ParameterSetting.Server.GROUP));
                    var siteId = server.get("siteId");
                    if (siteId != null && siteId.isString() && StringUtils.hasText(siteId.asString())) {
                        return Mono.empty();
                    }
                    server.put("siteId", UUID.randomUUID().toString());
                    configMap.getData().put(ParameterSetting.Server.GROUP, server.toString());
                    return client.update(configMap)
                            .retryWhen(Retry.backoff(5, Duration.ofMillis(100))
                                    .filter(OptimisticLockingFailureException.class::isInstance))
                            .doOnSuccess(updated -> log.info("Initialized the parameter settings site ID"));
                })
                .then();
    }

    private static ObjectNode readServer(String json) {
        if (!StringUtils.hasText(json)) {
            return JsonMapper.shared().createObjectNode();
        }
        try {
            var node = JsonMapper.shared().readTree(json);
            return node.isObject() ? (ObjectNode) node : JsonMapper.shared().createObjectNode();
        } catch (RuntimeException e) {
            log.warn("Ignoring malformed parameter server group JSON");
            return JsonMapper.shared().createObjectNode();
        }
    }
}
