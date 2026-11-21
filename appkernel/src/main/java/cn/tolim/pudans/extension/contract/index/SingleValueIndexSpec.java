package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import org.jspecify.annotations.Nullable;


public interface SingleValueIndexSpec<E extends Extension, K extends Comparable<K>> extends ValueIndexSpec<E, K> {

    // 创建单值索引构建器。
    static <E extends Extension, K extends Comparable<K>> SingleValueBuilder<E, K> builder(
            String name, Class<K> keyType) {
        return new SingleValueBuilder<>(name, keyType);
    }

    // 提取扩展的单个索引键。
    @Nullable
    K getValue(E extension);
}
