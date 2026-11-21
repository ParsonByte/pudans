package cn.tolim.pudans.extension.contract;

import cn.tolim.pudans.extension.contract.index.IndexedQueryEngine;
import java.util.Comparator;
import java.util.function.Predicate;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


public interface ReactiveExtensionClient {


    // 按谓词与比较器过滤并排序扩展。
    <E extends Extension> Flux<E> list(Class<E> type, Predicate<E> predicate, Comparator<E> comparator);


    // 按选择条件列出全部扩展。
    <E extends Extension> Flux<E> listAll(Class<E> type, ListOptions options, Sort sort);

    // 只返回名称，避免加载完整对象。
    <E extends Extension> Flux<String> listAllNames(Class<E> type, ListOptions options, Sort sort);

    // 按排序取前 N 个名称。
    <E extends Extension> Flux<String> listTopNames(Class<E> type, ListOptions options, Sort sort, int topN);

    // 分页查询扩展列表。
    <E extends Extension> Mono<ListResult<E>> listBy(Class<E> type, ListOptions options, PageRequest pageable);

    // 分页查询名称列表。
    <E extends Extension> Mono<ListResult<String>> listNamesBy(
            Class<E> type, ListOptions options, PageRequest pageable);

    // 统计满足选择条件的扩展数量。
    <E extends Extension> Mono<Long> countBy(Class<E> type, ListOptions options);


    // 按名称读取扩展，不存在时返回空。
    <E extends Extension> Mono<E> fetch(Class<E> type, String name);

    // 按资源标识读取非结构化扩展。
    Mono<Unstructured> fetch(GroupVersionKind gvk, String name);

    // 读取扩展，缺失时以错误终止。
    <E extends Extension> Mono<E> get(Class<E> type, String name);

    // 以 JSON 视图读取原始数据，缺失时报错。
    Mono<JsonExtension> getJsonExtension(GroupVersionKind gvk, String name);


    // 创建扩展并保护客户端可写元数据。
    <E extends Extension> Mono<E> create(E extension);


    // 更新扩展并返回持久化后的结果。
    <E extends Extension> Mono<E> update(E extension);


    // 删除扩展并触发终结器流程。
    <E extends Extension> Mono<E> delete(E extension);

    // 返回底层索引查询引擎。
    IndexedQueryEngine indexedQueryEngine();

    // 注册监听器以接收扩展变更事件。
    void watch(Watcher watcher);
}
