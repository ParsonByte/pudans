package cn.tolim.pudans.extension.index;

import static java.util.stream.Collectors.toUnmodifiableMap;

import cn.tolim.pudans.extension.contract.Extension;
import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;


class LabelIndex<E extends Extension> implements LabelIndexQuery, Index<E, String> {

    private final ConcurrentNavigableMap<LabelEntry, Set<String>> index;

    private final ConcurrentMap<String, Set<LabelEntry>> invertedIndex;

    // 初始化标签正排与倒排索引。
    public LabelIndex() {
        this.index = new ConcurrentSkipListMap<>();
        this.invertedIndex = new ConcurrentHashMap<>();
    }

    // 清空标签索引数据。
    @Override
    public void close() throws IOException {
        this.index.clear();
        this.invertedIndex.clear();
    }

    // 返回固定标签索引名称。
    @Override
    public String getName() {
        return "metadata.labels";
    }

    // 标签索引键固定为字符串。
    @Override
    public Class<String> getKeyType() {
        return String.class;
    }

    // 准备标签写入事务操作。
    @Override
    public TransactionalOperation prepareInsert(E extension) {
        var primaryKey = extension.getMetadata().getName();
        var labels = extension.getMetadata().getLabels();
        return new UpsertTransactionalOperation(primaryKey, labels);
    }

    // 准备标签更新事务操作。
    @Override
    public TransactionalOperation prepareUpdate(E extension) {
        var primaryKey = extension.getMetadata().getName();
        var labels = extension.getMetadata().getLabels();
        return new UpsertTransactionalOperation(primaryKey, labels);
    }

    // 按主键准备标签删除事务操作。
    @Override
    public TransactionalOperation prepareDelete(String primaryKey) {
        return new DeleteTransactionalOperation(primaryKey);
    }

    // 返回包含指定标签键的主键集合。
    @Override
    public Set<String> exists(String labelKey) {
        return index
                .subMap(
                        new LabelEntry(labelKey, null), true,
                        new LabelEntry(labelKey, Character.MAX_VALUE + ""), true)
                .values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // 返回标签键值完全匹配的主键集合。
    @Override
    public Set<String> equal(String labelKey, String labelValue) {
        var primaryKeys = index.get(new LabelEntry(labelKey, labelValue));
        return primaryKeys == null ? new HashSet<>() : new HashSet<>(primaryKeys);
    }

    // 返回同键但值不同的主键集合。
    @Override
    public Set<String> notEqual(String labelKey, String labelValue) {

        var labelEntry = new LabelEntry(labelKey, labelValue);
        return index
                .subMap(
                        new LabelEntry(labelKey, null), true,
                        new LabelEntry(labelKey, Character.MAX_VALUE + ""), true)
                .entrySet()
                .stream()
                .filter(entry -> !Objects.equals(entry.getKey(), labelEntry))
                .map(Map.Entry::getValue)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // 返回标签值命中集合的主键。
    @Override
    public Set<String> in(String labelKey, Collection<String> labelValues) {
        if (CollectionUtils.isEmpty(labelValues)) {
            return new HashSet<>();
        }
        return labelValues.stream()
                .distinct()
                .map(labelValue -> new LabelEntry(labelKey, labelValue))
                .map(index::get)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // 返回标签值不在集合中的主键。
    @Override
    public Set<String> notIn(String labelKey, Collection<String> labelValues) {
        if (CollectionUtils.isEmpty(labelValues)) {
            return new HashSet<>();
        }
        var valueSet = labelValues instanceof Set<String> set ? set : Set.copyOf(labelValues);
        return index
                .subMap(
                        new LabelEntry(labelKey, null), true,
                        new LabelEntry(labelKey, Character.MAX_VALUE + ""), true)
                .entrySet()
                .stream()
                .filter(entry -> !valueSet.contains(entry.getKey().labelValue()))
                .map(Map.Entry::getValue)
                .flatMap(Set::stream)
                .collect(Collectors.toCollection(HashSet::new));
    }

    // 从正排与倒排索引移除标签。
    private void removeLabels(String primaryKey, Map<String, String> labels) {
        if (CollectionUtils.isEmpty(labels)) {
            return;
        }
        invertedIndex.remove(primaryKey);
        labels.forEach((labelKey, labelValue) -> {
            var labelEntry = new LabelEntry(labelKey, labelValue);
            index.computeIfPresent(labelEntry, (key, primaryKeys) -> {
                primaryKeys.remove(primaryKey);
                if (primaryKeys.isEmpty()) {
                    return null;
                }
                return primaryKeys;
            });
        });
    }

    // 把标签写入正排与倒排索引。
    private void addLabels(String primaryKey, Map<String, String> labels) {
        if (CollectionUtils.isEmpty(labels)) {
            return;
        }
        labels.forEach((labelKey, labelValue) -> {
            var labelEntry = new LabelEntry(labelKey, labelValue);
            index.compute(labelEntry, (key, primaryKeys) -> {
                if (primaryKeys == null) {
                    primaryKeys = ConcurrentHashMap.newKeySet();
                }
                primaryKeys.add(primaryKey);
                return primaryKeys;
            });
            invertedIndex.compute(primaryKey, (pk, labelEntries) -> {
                if (labelEntries == null) {
                    labelEntries = ConcurrentHashMap.newKeySet();
                }
                labelEntries.add(labelEntry);
                return labelEntries;
            });
        });
    }

    record LabelEntry(String labelKey, @Nullable String labelValue) implements Comparable<LabelEntry> {

        // 校验标签键非空。
        public LabelEntry {
            Objects.requireNonNull(labelKey, "labelKey 不能为 null");
        }

        // 先按键再按值排序，空值优先。
        @Override
        public int compareTo(LabelEntry o) {
            var compare = Comparator.<String>naturalOrder().compare(this.labelKey, o.labelKey);
            if (compare != 0) {
                return compare;
            }
            return Comparator.nullsFirst(Comparator.<String>naturalOrder()).compare(this.labelValue, o.labelValue);
        }
    }

    class UpsertTransactionalOperation implements TransactionalOperation {

        private final String primaryKey;

        private final Map<String, String> labels;

        private Map<String, String> previousLabels;

        private boolean committed;

        // 记录主键与目标标签快照。
        UpsertTransactionalOperation(String primaryKey, Map<String, String> labels) {
            this.primaryKey = primaryKey;
            this.labels = labels;
        }

        // 保存旧标签以支持回滚。
        @Override
        public void prepare() {
            this.previousLabels = Optional.ofNullable(invertedIndex.get(primaryKey))
                    .map(labelEntries -> labelEntries.stream()
                            .filter(entry -> entry.labelValue() != null)
                            .collect(toUnmodifiableMap(LabelEntry::labelKey, LabelEntry::labelValue)))
                    .orElse(null);
        }

        // 幂等提交，替换该主键的标签。
        @Override
        public void commit() {
            if (committed) {
                return;
            }
            this.committed = true;

            removeLabels(primaryKey, previousLabels);
            addLabels(primaryKey, labels);
        }

        // 撤销提交，恢复旧标签。
        @Override
        public void rollback() {
            if (!committed) {
                return;
            }
            removeLabels(primaryKey, labels);
            addLabels(primaryKey, previousLabels);
        }
    }

    class DeleteTransactionalOperation implements TransactionalOperation {

        private final String primaryKey;

        private Map<String, String> previousLabels;

        private boolean committed;

        // 记录待删除的主键。
        DeleteTransactionalOperation(String primaryKey) {
            this.primaryKey = primaryKey;
        }

        // 保存待删除主键的标签。
        @Override
        public void prepare() {
            this.previousLabels = Optional.ofNullable(invertedIndex.get(primaryKey))
                    .map(labelEntries -> labelEntries.stream()
                            .filter(entry -> entry.labelValue() != null)
                            .collect(toUnmodifiableMap(LabelEntry::labelKey, LabelEntry::labelValue)))
                    .orElse(null);
        }

        // 幂等删除主键的全部标签。
        @Override
        public void commit() {
            if (committed) {
                return;
            }
            this.committed = true;
            removeLabels(primaryKey, previousLabels);
        }

        // 恢复删除前的标签。
        @Override
        public void rollback() {
            if (!committed) {
                return;
            }
            addLabels(primaryKey, previousLabels);
        }
    }
}
