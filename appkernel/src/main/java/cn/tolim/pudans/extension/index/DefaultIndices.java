package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;


@Slf4j
class DefaultIndices<E extends Extension> implements Indices<E> {

    private final Map<String, Index<E, ?>> indexMap;

    private final LockCache lockCache = new LockCache();

    private volatile boolean closed;

    // 按名称建立索引映射，重复名称保留先者。
    public DefaultIndices(List<Index<E, ?>> indices) {
        this.indexMap = indices.stream()
                .collect(Collectors.toMap(
                        Index::getName,
                        Function.identity(),

                        (existing, replacing) -> existing,

                        LinkedHashMap::new));
    }

    // 关闭全部索引并清理锁缓存。
    @Override
    public void close() throws IOException {
        closed = true;
        IOUtils.close(indexMap.values().toArray(Index[]::new));
        lockCache.clear();
    }

    // 在扩展主键锁内事务化写入所有索引。
    @Override
    public void insert(E extension) {
        ensureNotClosed();

        var primaryKey = extension.getMetadata().getName();
        var ops = new ArrayList<TransactionalOperation>();
        try (var ignored = lockCache.acquire(primaryKey)) {
            try {
                for (var index : indexMap.values()) {
                    var op = index.prepareInsert(extension);
                    op.prepare();
                    ops.add(op);
                }
                ops.forEach(TransactionalOperation::commit);
            } catch (Exception e) {
                log.warn("插入扩展 {} 失败，正在尝试回滚", primaryKey, e);
                ops.forEach(TransactionalOperation::rollback);
                throw e;
            }
        }
    }

    // 在扩展主键锁内事务化更新所有索引。
    @Override
    public void update(E extension) {
        ensureNotClosed();
        var primaryKey = extension.getMetadata().getName();
        var updaters = new ArrayList<TransactionalOperation>();
        try (var ignored = lockCache.acquire(primaryKey)) {
            try {
                for (var index : indexMap.values()) {
                    var updater = index.prepareUpdate(extension);
                    updater.prepare();
                    updaters.add(updater);
                }
                updaters.forEach(TransactionalOperation::commit);
            } catch (Exception e) {
                updaters.forEach(TransactionalOperation::rollback);
                throw e;
            }
        }
    }

    // 在扩展主键锁内事务化删除所有索引。
    @Override
    public void delete(E extension) {
        ensureNotClosed();
        var primaryKey = extension.getMetadata().getName();
        var updaters = new ArrayList<TransactionalOperation>();
        try (var ignored = lockCache.acquire(primaryKey)) {
            try {
                for (var index : indexMap.values()) {
                    var updater = index.prepareDelete(primaryKey);
                    updater.prepare();
                    updaters.add(updater);
                }
                updaters.forEach(TransactionalOperation::commit);
            } catch (Exception e) {
                updaters.forEach(TransactionalOperation::rollback);
                throw e;
            }
        }
    }

    // 按名称获取索引，缺失时抛出参数错误。
    @Override
    public <K extends Comparable<K>> Index<E, K> getIndex(String indexName) {
        ensureNotClosed();
        var index = (Index<E, K>) indexMap.get(indexName);
        if (index == null) {
            throw new IllegalArgumentException("未找到具有以下名称的索引：" + indexName);
        }
        return index;
    }

    // 索引关闭后拒绝任何读写操作。
    private void ensureNotClosed() {
        if (closed) {
            throw new IllegalStateException("索引已关闭");
        }
    }
}
