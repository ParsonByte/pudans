package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;


public interface ParameterSettingFetcher {

    <T> Mono<T> fetch(String key, Class<T> type);

    Mono<ParameterSetting.Basic> getBasic();


    Mono<Map<String, String>> getConfig();


    Mono<ConfigMap> getConfigMap();


    Optional<ConfigMap> getConfigMapBlocking();
}
