package cn.tolim.pudans.setting;

import reactor.core.publisher.Mono;
import tools.jackson.databind.node.ObjectNode;


public interface ConfigMaps {

    // 新增或更新指定 ConfigMap 的 JSON 数据
    Mono<Void> upsertConfig(String configMapName, ObjectNode configJsonData);

    // 读取指定 ConfigMap 的 JSON 数据
    Mono<ObjectNode> fetchConfig(String configMapName);
}
