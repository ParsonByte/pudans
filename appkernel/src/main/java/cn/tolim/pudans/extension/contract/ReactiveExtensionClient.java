package cn.tolim.pudans.extension.contract;

import cn.tolim.pudans.extension.contract.index.IndexedQueryEngine;
import java.util.Comparator;
import java.util.function.Predicate;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface ReactiveExtensionClient {


    <E extends Extension> Flux<E> list(Class<E> type, Predicate<E> predicate, Comparator<E> comparator);


    <E extends Extension> Flux<E> listAll(Class<E> type, ListOptions options, Sort sort);

    <E extends Extension> Flux<String> listAllNames(Class<E> type, ListOptions options, Sort sort);

    <E extends Extension> Flux<String> listTopNames(Class<E> type, ListOptions options, Sort sort, int topN);

    <E extends Extension> Mono<ListResult<E>> listBy(Class<E> type, ListOptions options, PageRequest pageable);

    <E extends Extension> Mono<ListResult<String>> listNamesBy(
            Class<E> type, ListOptions options, PageRequest pageable);

    <E extends Extension> Mono<Long> countBy(Class<E> type, ListOptions options);


    <E extends Extension> Mono<E> fetch(Class<E> type, String name);

    Mono<Unstructured> fetch(GroupVersionKind gvk, String name);

    <E extends Extension> Mono<E> get(Class<E> type, String name);

    Mono<JsonExtension> getJsonExtension(GroupVersionKind gvk, String name);


    <E extends Extension> Mono<E> create(E extension);


    <E extends Extension> Mono<E> update(E extension);


    <E extends Extension> Mono<E> delete(E extension);

    IndexedQueryEngine indexedQueryEngine();

    void watch(Watcher watcher);
}
