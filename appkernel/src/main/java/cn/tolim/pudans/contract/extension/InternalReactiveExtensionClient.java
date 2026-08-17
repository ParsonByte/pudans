package cn.tolim.pudans.contract.extension;

import reactor.core.publisher.Mono;

public interface InternalReactiveExtensionClient extends ReactiveExtensionClient {

    <E extends Extension> Mono<E> createInternal(E extension);

    <E extends Extension> Mono<E> updateInternal(E extension);
}
