package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import cn.tolim.pudans.contract.extension.index.MultiValueIndexSpec;
import cn.tolim.pudans.contract.extension.index.UnknownKey;
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
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;


@Slf4j
class MultiValueIndex<E extends Extension, K extends Comparable<K>> implements ValueIndexQuery<K>, Index<E, K> {

    private final ConcurrentNavigableMap<K, Set<String>> index;

    private final ConcurrentMap<String, Set<K>> invertedIndex;

    private final Set<String> nullKeyValues;

    private final MultiValueIndexSpec<E, K> spec;

    public MultiValueIndex(MultiValueIndexSpec<E, K> spec) {
        this.spec = spec;
        this.index = new ConcurrentSkipListMap<>(Comparator.naturalOrder());
        this.invertedIndex = new ConcurrentHashMap<>();
        this.nullKeyValues = ConcurrentHashMap.newKeySet();
    }

    @Override
    public void close() throws IOException {
        this.index.clear();
        this.invertedIndex.clear();
        this.nullKeyValues.clear();
    }

    @Override
    public String getName() {
        return spec.getName();
    }

    @Override
    public Class<K> getKeyType() {
        return spec.getKeyType();
    }

    @Override
    public boolean isUnique() {
        return spec.isUnique();
    }

    @Override
    public TransactionalOperation prepareInsert(E extension) {
        var keys = spec.getValues(extension);
        return new UpsertTransactionalOperation(extension.getMetadata().getName(), keys);
    }

    @Override
    public TransactionalOperation prepareUpdate(E extension) {

        var newKeys = spec.getValues(extension);
        var primaryKey = extension.getMetadata().getName();
        return new UpsertTransactionalOperation(primaryKey, newKeys);
    }

    @Override
    public TransactionalOperation prepareDelete(String primaryKey) {
        return new DeleteTransactionalOperation(primaryKey);
    }


    Set<K> getKeys(String primaryKey) {
        return Collections.unmodifiableSet(invertedIndex.getOrDefault(primaryKey, Set.of()));
    }

    @Override
    public Set<String> between(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        throw new UnsupportedOperationException("Multi-value index does not support between operation");
    }

    @Override
    public Set<String> notBetween(K fromKey, boolean fromInclusive, K toKey, boolean toInclusive) {
        throw new UnsupportedOperationException("Multi-value index does not support notBetween operation");
    }

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

    @Override
    public Set<String> lessThan(K key, boolean inclusive) {
        throw new UnsupportedOperationException("Multi-value index does not support lessThan operation");
    }

    @Override
    public Set<String> greaterThan(K key, boolean inclusive) {
        throw new UnsupportedOperationException("Multi-value index does not support greaterThan operation");
    }

    @Override
    public Set<String> isNull() {
        return new HashSet<>(nullKeyValues);
    }

    @Override
    public Set<String> isNotNull() {
        return index.values().stream().flatMap(Set::stream).collect(Collectors.toCollection(HashSet::new));
    }

    @Override
    public Set<String> stringContains(String keyword) {
        throw new UnsupportedOperationException("Multi-value index does not support stringContains operation");
    }

    @Override
    public Set<String> stringNotContains(String keyword) {
        throw new UnsupportedOperationException("Multi-value index does not support stringNotContains operation");
    }

    @Override
    public Set<String> stringStartsWith(String prefix) {
        throw new UnsupportedOperationException("Multi-value index does not support stringStartsWith operation");
    }

    @Override
    public Set<String> stringNotStartsWith(String prefix) {
        throw new UnsupportedOperationException("Multi-value index does not support stringNotStartsWith operation");
    }

    @Override
    public Set<String> stringEndsWith(String suffix) {
        throw new UnsupportedOperationException("Multi-value index does not support stringEndsWith operation");
    }

    @Override
    public Set<String> stringNotEndsWith(String suffix) {
        throw new UnsupportedOperationException("Multi-value index does not support stringNotEndsWith operation");
    }

    @Override
    public Set<String> notEqual(K key) {
        return notIn(Collections.singleton(key));
    }

    @Override
    public Set<String> equal(K key) {
        var primaryKeys = index.get(key);
        return primaryKeys == null ? new HashSet<>() : new HashSet<>(primaryKeys);
    }

    @Override
    public Set<String> all() {
        return Stream.concat(index.values().stream(), Stream.of(nullKeyValues))
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private void ensureStringKeyType() {
        Assert.isTrue(
                getKeyType() == String.class || getKeyType() == UnknownKey.class,
                "Key type must be String for this operation");
    }

    class UpsertTransactionalOperation implements TransactionalOperation {

        private final String primaryKey;

        private final @Nullable Set<K> newKeys;

        private boolean committed;

        private Set<K> previousKeys;

        private boolean previousNullKey;

        UpsertTransactionalOperation(String primaryKey, @Nullable Set<K> newKeys) {
            this.primaryKey = primaryKey;
            this.newKeys = newKeys;
        }

        @Override
        public void prepare() {
            this.previousKeys = invertedIndex.get(primaryKey);
            this.previousNullKey = nullKeyValues.contains(primaryKey);
        }

        @Override
        public void commit() {
            if (committed) {
                return;
            }
            committed = true;
            if (Objects.equals(previousKeys, newKeys)) {
                return;
            }
            invertedIndex.put(primaryKey, newKeys);

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
                                    String.format("Duplicate key '%s' for extension '%s'", k, primaryKey));
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

        DeleteTransactionalOperation(String primaryKey) {
            this.primaryKey = primaryKey;
        }

        @Override
        public void prepare() {
            this.previousKeys = invertedIndex.get(primaryKey);
            this.previousNullKey = nullKeyValues.contains(primaryKey);
        }

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
