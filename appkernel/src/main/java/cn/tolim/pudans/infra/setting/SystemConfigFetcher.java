package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.infra.SystemSetting;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;


public interface SystemConfigFetcher {

    <T> Mono<T> fetch(String key, Class<T> type);

    Mono<SystemSetting.Basic> getBasic();


    Mono<Map<String, String>> getConfig();


    Mono<ConfigMap> getConfigMap();


    Optional<ConfigMap> getConfigMapBlocking();
}
