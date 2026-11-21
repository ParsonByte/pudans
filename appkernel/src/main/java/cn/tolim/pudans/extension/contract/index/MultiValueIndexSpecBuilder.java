package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.util.Set;
import java.util.function.Function;


public interface MultiValueIndexSpecBuilder<E extends Extension, K extends Comparable<K>>
        extends IndexSpecBuilder<E, K, MultiValueIndexSpecBuilder<E, K>> {


    // 设置从扩展提取多值键的函数。
    MultiValueIndexSpecBuilder<E, K> indexFunc(Function<E, Set<K>> indexFunc);
}
