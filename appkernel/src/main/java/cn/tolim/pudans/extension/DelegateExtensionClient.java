package cn.tolim.pudans.extension;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.extension.contract.index.IndexedQueryEngine;
import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.contract.InternalReactiveExtensionClient;
import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.ListResult;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.PageRequest;
import cn.tolim.pudans.extension.contract.GroupVersionKind;
import cn.tolim.pudans.extension.contract.Unstructured;
import cn.tolim.pudans.extension.contract.Watcher;

@Component
public class DelegateExtensionClient implements ExtensionClient {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final InternalReactiveExtensionClient client;

    // 保存底层响应式客户端用于阻塞调用。
    public DelegateExtensionClient(InternalReactiveExtensionClient client) {
        this.client = client;
    }

    // 阻塞等待并按条件过滤排序扩展列表。
    @Override
    public <E extends Extension> List<E> list(Class<E> type, Predicate<E> predicate, Comparator<E> comparator) {
        return client.list(type, predicate, comparator).collectList().block(TIMEOUT);
    }

    // 按选项与排序阻塞拉取全部扩展。
    @Override
    public <E extends Extension> List<E> listAll(Class<E> type, ListOptions options, Sort sort) {
        return client.listAll(type, options, sort).collectList().block(TIMEOUT);
    }

    // 仅阻塞拉取满足条件的扩展名称。
    @Override
    public <E extends Extension> List<String> listAllNames(Class<E> type, ListOptions options, Sort sort) {
        return client.listAllNames(type, options, sort).collectList().block(TIMEOUT);
    }

    // 阻塞拉取排序靠前的指定数量名称。
    @Override
    public <E extends Extension> List<String> listTopNames(Class<E> type, ListOptions options, Sort sort, int topN) {
        return client.listTopNames(type, options, sort, topN).collectList().block(TIMEOUT);
    }

    // 分页阻塞查询并返回结果与总数。
    @Override
    public <E extends Extension> ListResult<E> listBy(Class<E> type, ListOptions options, PageRequest page) {
        return client.listBy(type, options, page).block(TIMEOUT);
    }

    // 分页阻塞查询扩展名称列表。
    @Override
    public <E extends Extension> ListResult<String> listNamesBy(Class<E> type, ListOptions options, PageRequest page) {
        return client.listNamesBy(type, options, page).block(TIMEOUT);
    }

    // 统计数量，空结果按零处理。
    @Override
    public <E extends Extension> long countBy(Class<E> type, ListOptions options) {
        return client.countBy(type, options).blockOptional(TIMEOUT).orElse(0L);
    }

    // 按类型与名称读取单个扩展。
    @Override
    public <E extends Extension> Optional<E> fetch(Class<E> type, String name) {
        return client.fetch(type, name).blockOptional(TIMEOUT);
    }

    // 按资源标识读取非结构化扩展。
    @Override
    public Optional<Unstructured> fetch(GroupVersionKind gvk, String name) {
        return client.fetch(gvk, name).blockOptional(TIMEOUT);
    }

    // 阻塞等待创建事务完成。
    @Override
    public <E extends Extension> void create(E extension) {
        client.createInternal(extension).block(TIMEOUT);
    }

    // 阻塞等待更新事务完成。
    @Override
    public <E extends Extension> void update(E extension) {
        client.updateInternal(extension).block(TIMEOUT);
    }

    // 阻塞等待删除事务完成。
    @Override
    public <E extends Extension> void delete(E extension) {
        client.delete(extension).block(TIMEOUT);
    }

    // 直接复用底层索引查询引擎。
    @Override
    public IndexedQueryEngine indexedQueryEngine() {
        return client.indexedQueryEngine();
    }

    // 注册观察者以接收扩展变更通知。
    @Override
    public void watch(Watcher watcher) {
        client.watch(watcher);
    }
}
