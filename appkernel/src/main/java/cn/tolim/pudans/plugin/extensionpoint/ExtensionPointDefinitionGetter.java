package cn.tolim.pudans.plugin.extensionpoint;

import reactor.core.publisher.Mono;

public interface ExtensionPointDefinitionGetter {


    Mono<ExtensionPointDefinition> getByClassName(String className);
}
