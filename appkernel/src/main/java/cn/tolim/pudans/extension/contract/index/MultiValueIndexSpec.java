package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.util.Set;
import org.jspecify.annotations.Nullable;


public interface MultiValueIndexSpec<E extends Extension, K extends Comparable<K>> extends ValueIndexSpec<E, K> {

    // 创建多值索引构建器。
    static <E extends Extension, K extends Comparable<K>> MultiValueBuilder<E, K> builder(
            String name, Class<K> keyType) {
        return new MultiValueBuilder<>(name, keyType);
    }

    // 提取扩展的全部索引键。
    @Nullable
    Set<K> getValues(E extension);
}
