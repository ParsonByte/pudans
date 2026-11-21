package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;


public interface SingleValueIndexSpecBuilder<E extends Extension, K extends Comparable<K>>
        extends IndexSpecBuilder<E, K, SingleValueIndexSpecBuilder<E, K>> {


    // 设置从扩展提取单值键的函数。
    SingleValueIndexSpecBuilder<E, K> indexFunc(Function<E, @Nullable K> indexFunc);
}
