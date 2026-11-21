package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.index.IndexSpecs;
import cn.tolim.pudans.extension.contract.index.MultiValueIndexSpec;
import cn.tolim.pudans.extension.contract.index.SingleValueIndexSpec;
import cn.tolim.pudans.extension.contract.index.ValueIndexSpec;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Stream;
import org.apache.commons.io.IOUtils;


class DefaultIndicesManager implements IndicesManager {

    private final ConcurrentMap<Class<? extends Extension>, Indices<? extends Extension>> indicesMap;

    // 创建并发安全的类型到索引映射。
    DefaultIndicesManager() {
        indicesMap = new ConcurrentHashMap<>();
    }

    // 合并默认索引与自定义索引并注册。
    @Override
    public <E extends Extension> void add(Class<E> type, List<ValueIndexSpec<E, ?>> indexSpecs) {
        indicesMap.computeIfAbsent(type, t -> {
            var indices = new ArrayList<Index<E, ?>>();

            Stream.concat(this.<E>createDefaultIndexSpecs().stream(), indexSpecs.stream())
                    .distinct()
                    .forEach(indexSpec -> {
                        if (indexSpec instanceof MultiValueIndexSpec<E, ?> spec) {
                            indices.add(new MultiValueIndex<>(spec));
                        } else if (indexSpec instanceof SingleValueIndexSpec<E, ?> spec) {
                            indices.add(new SingleValueIndex<>(spec));
                        }

                    });
            indices.add(new LabelIndex<>());
            return new DefaultIndices<>(indices);
        });
    }

    // 关闭并清空全部类型索引。
    @Override
    public void close() throws IOException {
        IOUtils.close(indicesMap.values().toArray(Indices[]::new));
        indicesMap.clear();
    }

    // 按类型获取索引，缺失时抛出参数错误。
    @Override
    public <E extends Extension> Indices<E> get(Class<E> type) {
        var indices = (Indices<E>) indicesMap.get(type);
        if (indices == null) {
            throw new IllegalArgumentException("未找到以下类型的索引：" + type.getName());
        }
        return indices;
    }

    // 移除并静默关闭类型的索引。
    @Override
    public <E extends Extension> void remove(Class<E> type) {
        var indices = indicesMap.remove(type);
        IOUtils.closeQuietly(indices);
    }

    // 提供名称与时间戳的默认索引。
    private <E extends Extension> List<ValueIndexSpec<E, ?>> createDefaultIndexSpecs() {
        var metadataNameSpec = IndexSpecs.<E, String>single("metadata.name", String.class)
                .indexFunc(e -> e.getMetadata().getName())
                .unique(true)
                .nullable(false)
                .build();
        var creationTimestampSpec = IndexSpecs.<E, Instant>single("metadata.creationTimestamp", Instant.class)
                .indexFunc(e -> e.getMetadata().getCreationTimestamp())
                .unique(false)
                .nullable(false)
                .build();
        var deletionTimestampSpec = IndexSpecs.<E, Instant>single("metadata.deletionTimestamp", Instant.class)
                .indexFunc(e -> e.getMetadata().getDeletionTimestamp())
                .unique(false)
                .nullable(true)
                .build();
        return List.of(metadataNameSpec, creationTimestampSpec, deletionTimestampSpec);
    }
}
