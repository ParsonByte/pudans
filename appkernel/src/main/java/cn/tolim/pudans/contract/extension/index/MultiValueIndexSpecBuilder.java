package cn.tolim.pudans.contract.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import java.util.Set;
import java.util.function.Function;


public interface MultiValueIndexSpecBuilder<E extends Extension, K extends Comparable<K>>
        extends IndexSpecBuilder<E, K, MultiValueIndexSpecBuilder<E, K>> {


    MultiValueIndexSpecBuilder<E, K> indexFunc(Function<E, Set<K>> indexFunc);
}
