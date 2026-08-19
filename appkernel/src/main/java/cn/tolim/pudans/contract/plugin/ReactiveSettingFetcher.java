package cn.tolim.pudans.contract.plugin;

import java.util.Map;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;


public interface ReactiveSettingFetcher {

    <T> Mono<T> fetch(String group, Class<T> clazz);


    Mono<JsonNode> getSettingValue(String group);


    Mono<Map<String, JsonNode>> getSettingValues();
}
