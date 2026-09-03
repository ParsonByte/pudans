package cn.tolim.pudans.setting.service;

import reactor.core.publisher.Mono;
import tools.jackson.databind.node.ObjectNode;


public interface SettingConfigService {

    Mono<Void> upsertConfig(String configMapName, ObjectNode configJsonData);

    Mono<ObjectNode> fetchConfig(String configMapName);
}
