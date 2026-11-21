package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.extension.api.ConfigMap;
import cn.tolim.pudans.infra.api.ParameterSetting;
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
