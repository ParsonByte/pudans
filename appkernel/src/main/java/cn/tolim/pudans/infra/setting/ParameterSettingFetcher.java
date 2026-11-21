package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.infra.contract.ParameterSetting;
import java.util.Map;
import java.util.Optional;
import reactor.core.publisher.Mono;


public interface ParameterSettingFetcher {

    // 按键读取设置并转换为目标类型。
    <T> Mono<T> fetch(String key, Class<T> type);

    // 返回基础设置，未配置时给出默认值。
    Mono<ParameterSetting.Basic> getBasic();


    // 返回当前配置键值快照。
    Mono<Map<String, String>> getConfig();


    // 返回参数设置配置资源。
    Mono<ConfigMap> getConfigMap();


    // 阻塞读取配置资源，供启动期调用。
    Optional<ConfigMap> getConfigMapBlocking();
}
