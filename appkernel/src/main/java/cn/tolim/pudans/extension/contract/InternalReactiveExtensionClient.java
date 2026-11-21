package cn.tolim.pudans.extension.contract;

import reactor.core.publisher.Mono;

public interface InternalReactiveExtensionClient extends ReactiveExtensionClient {

    // 内部创建入口，供控制器写入扩展。
    <E extends Extension> Mono<E> createInternal(E extension);

    // 内部更新入口，供控制器写入扩展。
    <E extends Extension> Mono<E> updateInternal(E extension);
}
