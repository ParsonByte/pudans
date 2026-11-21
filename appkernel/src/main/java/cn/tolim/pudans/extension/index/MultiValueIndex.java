package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.index.MultiValueIndexSpec;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.CollectionUtils;


class MultiValueIndex<E extends Extension, K extends Comparable<K>> implements ValueIndexQuery<K>, Index<E, K> {

    private final ConcurrentNavigableMap<K, Set<String>> index;

    private final ConcurrentMap<String, Set<K>> invertedIndex;

    private final Set<String> nullKeyValues;

    private final MultiValueIndexSpec<E, K> spec;

    // 保存索引描述并初始化并发索引结构。
    public MultiValueIndex(MultiValueIndexSpec<E, K> spec) {
        this.spec = spec;
        this.index = new ConcurrentSkipListMap<>(Comparator.naturalOrder());
        this.invertedIndex = new ConcurrentHashMap<>();
        this.nullKeyValues = ConcurrentHashMap.newKeySet();
    }

    // 清空正排、倒排与空值集合。
    @Override
    public void close() throws IOException {
        this.index.clear();
        this.invertedIndex.clear();
        this.nullKeyValues.clear();
    }

    // 返回索引配置的名称。
    @Override
    public String getName() {
        return spec.getName();
    }

    // 返回索引配置的键类型。
    @Override
    public Class<K> getKeyType() {
        return spec.getKeyType();
    }

    // 返回索引配置的唯一性。
    @Override
    public boolean isUnique() {
        return spec.isUnique();
    }

    // 准备基于旧键集合的写入事务。
    @Override
    public TransactionalOperation prepareInsert(E extension) {
        var keys = spec.getValues(extension);
        return new UpsertTransactionalOperation(extension.getMetadata().getName(), keys);
    }

    // 准备基于新键集合的更新事务。
    @Override
    public TransactionalOperation prepareUpdate(E extension) {

        var newKeys = spec.getValues(extension);
        var primaryKey = extension.getMetadata().getName();
        return new UpsertTransactionalOperation(primaryKey, newKeys);
    }

    // 按主键准备删除事务。
    @Override
    public TransactionalOperation prepareDelete(String primaryKey) {
        return new DeleteTransactionalOperation(primaryKey);
    }


    // 返回主键对应的只读键集合。
    Set<K> getKeys(String primaryKey) {
        return Collections.unmodifiableSet(invertedIndex.getOrDefault(primaryKey, Set.of()));
    }

    // 多值索引不支持区间查询。
    @Override
    public Set<String> between(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        throw new UnsupportedOperationException("多值索引不支持 between 操作");
    }

    // 多值索引不支持区间排除查询。
    @Override
    public Set<String> notBetween(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        throw new UnsupportedOperationException("多值索引不支持 notBetween 操作");
    }

    // 返回键命中集合的主键。
    @Override
    public Set<String> in(Collection<K> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return new HashSet<>();
        }
        return keys.stream()
                .distinct()
                .map(index::get)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // 返回键不在集合中的主键。
    @Override
    public Set<String> notIn(Collection<K> keys) {
        if (CollectionUtils.isEmpty(keys)) {
            return all();
        }
        var inResult = in(keys);
        return index.values().stream()
                .flatMap(Set::stream)
                .filter(v -> !inResult.contains(v))
                .collect(Collectors.toCollection(HashSet::new));
    }

    // 多值索引不支持范围比较。
    @Override
    public Set<String> lessThan(K key, boolean inclusive) {
        throw new UnsupportedOperationException("多值索引不支持 lessThan 操作");
    }

    // 多值索引不支持范围比较。
    @Override
    public Set<String> greaterThan(K key, boolean inclusive) {
        throw new UnsupportedOperationException("多值索引不支持 greaterThan 操作");
    }

    // 返回没有索引键的主键集合。
    @Override
    public Set<String> isNull() {
        return new HashSet<>(nullKeyValues);
    }

    // 返回至少有一个索引键的主键集合。
    @Override
    public Set<String> isNotNull() {
        return index.values().stream().flatMap(Set::stream).collect(Collectors.toCollection(HashSet::new));
    }

    // 多值索引不支持字符串查询。
    @Override
    public Set<String> stringContains(String keyword) {
        throw new UnsupportedOperationException("多值索引不支持 stringContains 操作");
    }

    // 多值索引不支持字符串查询。
    @Override
    public Set<String> stringNotContains(String keyword) {
        throw new UnsupportedOperationException("多值索引不支持 stringNotContains 操作");
    }

    // 多值索引不支持字符串查询。
    @Override
    public Set<String> stringStartsWith(String prefix) {
        throw new UnsupportedOperationException("多值索引不支持 stringStartsWith 操作");
    }

    // 多值索引不支持字符串查询。
    @Override
    public Set<String> stringNotStartsWith(String prefix) {
        throw new UnsupportedOperationException("多值索引不支持 stringNotStartsWith 操作");
    }

    // 多值索引不支持字符串查询。
    @Override
    public Set<String> stringEndsWith(String suffix) {
        throw new UnsupportedOperationException("多值索引不支持 stringEndsWith 操作");
    }

    // 多值索引不支持字符串查询。
    @Override
    public Set<String> stringNotEndsWith(String suffix) {
        throw new UnsupportedOperationException("多值索引不支持 stringNotEndsWith 操作");
    }

    // 取补集实现键不相等查询。
    @Override
    public Set<String> notEqual(K key) {
        return notIn(Collections.singleton(key));
    }

    // 返回命中指定键的主键集合。
    @Override
    public Set<String> equal(K key) {
        var primaryKeys = index.get(key);
        return primaryKeys == null ? new HashSet<>() : new HashSet<>(primaryKeys);
    }

    // 合并有键与空键的主键集合。
    @Override
    public Set<String> all() {
        return Stream.concat(index.values().stream(), Stream.of(nullKeyValues))
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    class UpsertTransactionalOperation implements TransactionalOperation {

        private final String primaryKey;

        private final @Nullable Set<K> newKeys;

        private boolean committed;

        private Set<K> previousKeys;

        private boolean previousNullKey;

        // 记录主键与目标键集合。
        UpsertTransactionalOperation(String primaryKey, @Nullable Set<K> newKeys) {
            this.primaryKey = primaryKey;
            this.newKeys = newKeys;
        }

        // 保存旧键与空键标记。
        @Override
        public void prepare() {
            this.previousKeys = invertedIndex.get(primaryKey);
            this.previousNullKey = nullKeyValues.contains(primaryKey);
        }

        // 幂等提交，更新正排与倒排索引。
        @Override
        public void commit() {
            if (committed) {
                return;
            }
            committed = true;
            if (Objects.equals(previousKeys, newKeys)) {
                return;
            }
            if (newKeys == null) {
                invertedIndex.remove(primaryKey);
            } else {
                invertedIndex.put(primaryKey, newKeys);
            }

            if (!CollectionUtils.isEmpty(previousKeys)) {
                previousKeys.forEach(key -> index.computeIfPresent(key, (k, v) -> {
                    v.remove(primaryKey);
                    return v.isEmpty() ? null : v;
                }));
            }

            if (!CollectionUtils.isEmpty(newKeys)) {
                for (K key : newKeys) {
                    index.compute(key, (k, v) -> {
                        if (v == null) {
                            v = ConcurrentHashMap.newKeySet();
                        }
                        if (spec.isUnique() && !v.isEmpty()) {
                            throw new DuplicateKeyException(
                                    String.format("键 '%s' 在扩展 '%s' 中重复", k, primaryKey));
                        }
                        v.add(primaryKey);
                        return v;
                    });
                }
                nullKeyValues.remove(primaryKey);
            } else {
                nullKeyValues.add(primaryKey);
            }
        }

        // 撤销提交，恢复旧键与空键标记。
        @Override
        public void rollback() {
            if (Objects.equals(this.previousKeys, newKeys) || !committed) {
                return;
            }

            if (!CollectionUtils.isEmpty(newKeys)) {
                newKeys.forEach(key -> index.computeIfPresent(key, (k, v) -> {
                    v.remove(primaryKey);
                    return v.isEmpty() ? null : v;
                }));
            }

            if (this.previousKeys == null) {

                invertedIndex.remove(primaryKey);
            } else {
                this.previousKeys.forEach(key -> index.compute(key, (k, v) -> {
                    if (v == null) {
                        v = ConcurrentHashMap.newKeySet();
                    }

                    v.add(primaryKey);
                    return v;
                }));
                invertedIndex.put(primaryKey, this.previousKeys);
            }
            if (previousNullKey) {
                nullKeyValues.add(primaryKey);
            } else {
                nullKeyValues.remove(primaryKey);
            }
        }
    }

    class DeleteTransactionalOperation implements TransactionalOperation {

        private final String primaryKey;

        private boolean committed;

        private Set<K> previousKeys;

        private boolean previousNullKey;

        // 记录待删除的主键。
        DeleteTransactionalOperation(String primaryKey) {
            this.primaryKey = primaryKey;
        }

        // 保存待删除主键的旧键。
        @Override
        public void prepare() {
            this.previousKeys = invertedIndex.get(primaryKey);
            this.previousNullKey = nullKeyValues.contains(primaryKey);
        }

        // 幂等移除主键的全部索引项。
        @Override
        public void commit() {
            if (committed) {
                return;
            }
            committed = true;
            invertedIndex.remove(primaryKey);
            if (this.previousKeys != null) {
                this.previousKeys.forEach(key -> index.computeIfPresent(key, (k, v) -> {
                    v.remove(primaryKey);
                    return v.isEmpty() ? null : v;
                }));
            }
            nullKeyValues.remove(primaryKey);
        }

        // 恢复删除前的键与空键标记。
        @Override
        public void rollback() {
            if (this.previousKeys == null || !committed) {
                return;
            }
            if (previousNullKey) {
                nullKeyValues.add(primaryKey);
            } else {
                nullKeyValues.remove(primaryKey);
            }

            this.previousKeys.forEach(key -> index.compute(key, (k, v) -> {
                if (v == null) {
                    v = ConcurrentHashMap.newKeySet();
                }
                v.add(primaryKey);
                return v;
            }));
            invertedIndex.put(primaryKey, this.previousKeys);
        }
    }
}
