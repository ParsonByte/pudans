package cn.tolim.pudans.setting;

import reactor.core.publisher.Mono;
import tools.jackson.databind.node.ObjectNode;


public interface ConfigMaps {

    Mono<Void> upsertConfig(String configMapName, ObjectNode configJsonData);

    Mono<ObjectNode> fetchConfig(String configMapName);
}
