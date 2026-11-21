package cn.tolim.pudans.extension.api.index;

import cn.tolim.pudans.extension.api.Extension;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;


public interface SingleValueIndexSpecBuilder<E extends Extension, K extends Comparable<K>>
        extends IndexSpecBuilder<E, K, SingleValueIndexSpecBuilder<E, K>> {


    SingleValueIndexSpecBuilder<E, K> indexFunc(Function<E, @Nullable K> indexFunc);
}
