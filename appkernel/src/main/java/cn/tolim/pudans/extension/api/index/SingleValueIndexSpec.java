package cn.tolim.pudans.extension.api.index;

import cn.tolim.pudans.extension.api.Extension;
import org.jspecify.annotations.Nullable;


public interface SingleValueIndexSpec<E extends Extension, K extends Comparable<K>> extends ValueIndexSpec<E, K> {

    static <E extends Extension, K extends Comparable<K>> SingleValueBuilder<E, K> builder(
            String name, Class<K> keyType) {
        return new SingleValueBuilder<>(name, keyType);
    }

    @Nullable
    K getValue(E extension);
}
