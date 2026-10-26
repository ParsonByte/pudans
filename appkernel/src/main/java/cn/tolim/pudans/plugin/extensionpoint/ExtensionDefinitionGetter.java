package cn.tolim.pudans.plugin.extensionpoint;

import reactor.core.publisher.Mono;

public interface ExtensionDefinitionGetter {


    Mono<ExtensionDefinition> get(String name);
}
