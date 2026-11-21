package cn.tolim.pudans.extension.api.index;

import cn.tolim.pudans.extension.api.Extension;
import java.util.List;


public interface IndexSpecs<E extends Extension> {


    static <E extends Extension, K extends Comparable<K>> MultiValueIndexSpecBuilder<E, K> multi(
            String name, Class<K> keyType) {
        return new MultiValueBuilder<>(name, keyType);
    }

    static <E extends Extension, K extends Comparable<K>> SingleValueIndexSpecBuilder<E, K> single(
            String name, Class<K> keyType) {
        return new SingleValueBuilder<>(name, keyType);
    }

    default <K extends Comparable<K>> void add(IndexSpec<E, K> indexSpec) {
        add(indexSpec.normalize());
    }

    <K extends Comparable<K>> void add(ValueIndexSpec<E, K> indexSpec);

    default <K extends Comparable<K>> void add(IndexSpecBuilder<E, K, ?> builder) {
        add(builder.build());
    }

    List<ValueIndexSpec<E, ?>> getIndexSpecs();
}
