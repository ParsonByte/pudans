package cn.tolim.pudans.extension.contract;

import cn.tolim.pudans.extension.contract.index.IndexedQueryEngine;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.data.domain.Sort;


public interface ExtensionClient {


    // 按谓词与比较器过滤并排序扩展。
    <E extends Extension> List<E> list(Class<E> type, Predicate<E> predicate, Comparator<E> comparator);


    // 按选择条件列出全部扩展。
    <E extends Extension> List<E> listAll(Class<E> type, ListOptions options, Sort sort);

    // 只返回名称，避免加载完整对象。
    <E extends Extension> List<String> listAllNames(Class<E> type, ListOptions options, Sort sort);

    // 按排序取前 N 个名称。
    <E extends Extension> List<String> listTopNames(Class<E> type, ListOptions options, Sort sort, int topN);

    // 分页查询扩展列表。
    <E extends Extension> ListResult<E> listBy(Class<E> type, ListOptions options, PageRequest page);

    // 分页查询名称列表。
    <E extends Extension> ListResult<String> listNamesBy(Class<E> type, ListOptions options, PageRequest page);

    // 统计满足选择条件的扩展数量。
    <E extends Extension> long countBy(Class<E> type, ListOptions options);


    // 按名称读取扩展，不存在时返回空。
    <E extends Extension> Optional<E> fetch(Class<E> type, String name);

    // 按资源标识读取非结构化扩展。
    Optional<Unstructured> fetch(GroupVersionKind gvk, String name);


    // 创建扩展并保护客户端可写元数据。
    <E extends Extension> void create(E extension);


    // 更新扩展并持久化变更。
    <E extends Extension> void update(E extension);


    // 删除扩展并触发终结器流程。
    <E extends Extension> void delete(E extension);

    // 返回底层索引查询引擎。
    IndexedQueryEngine indexedQueryEngine();

    // 注册监听器以接收扩展变更事件。
    void watch(Watcher watcher);
}
