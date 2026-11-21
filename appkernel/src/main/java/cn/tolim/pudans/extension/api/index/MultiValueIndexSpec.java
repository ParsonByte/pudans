package cn.tolim.pudans.extension.api.index;

import cn.tolim.pudans.extension.api.Extension;
import java.util.Set;
import org.jspecify.annotations.Nullable;


public interface MultiValueIndexSpec<E extends Extension, K extends Comparable<K>> extends ValueIndexSpec<E, K> {

    static <E extends Extension, K extends Comparable<K>> MultiValueBuilder<E, K> builder(
            String name, Class<K> keyType) {
        return new MultiValueBuilder<>(name, keyType);
    }

    @Nullable
    Set<K> getValues(E extension);
}
