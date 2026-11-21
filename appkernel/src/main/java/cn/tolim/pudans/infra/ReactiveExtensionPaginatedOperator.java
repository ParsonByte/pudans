package cn.tolim.pudans.infra;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ListOptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface ReactiveExtensionPaginatedOperator {


    <E extends Extension> Mono<Void> deleteContinuously(Class<E> type, ListOptions listOptions);


    <E extends Extension> Flux<E> deleteInitialBatch(Class<E> type, ListOptions listOptions);


    <E extends Extension> Flux<E> list(Class<E> type, ListOptions listOptions);
}
