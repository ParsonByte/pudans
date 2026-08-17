package cn.tolim.pudans.contract.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;


public interface SingleValueIndexSpecBuilder<E extends Extension, K extends Comparable<K>>
        extends IndexSpecBuilder<E, K, SingleValueIndexSpecBuilder<E, K>> {


    SingleValueIndexSpecBuilder<E, K> indexFunc(Function<E, @Nullable K> indexFunc);
}
