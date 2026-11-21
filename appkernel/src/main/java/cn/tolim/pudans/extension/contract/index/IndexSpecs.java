package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.util.List;


public interface IndexSpecs<E extends Extension> {


    // 创建多值索引构建器。
    static <E extends Extension, K extends Comparable<K>> MultiValueIndexSpecBuilder<E, K> multi(
            String name, Class<K> keyType) {
        return new MultiValueBuilder<>(name, keyType);
    }

    // 创建单值索引构建器。
    static <E extends Extension, K extends Comparable<K>> SingleValueIndexSpecBuilder<E, K> single(
            String name, Class<K> keyType) {
        return new SingleValueBuilder<>(name, keyType);
    }

    // 规范化后加入索引集合。
    default <K extends Comparable<K>> void add(IndexSpec<E, K> indexSpec) {
        add(indexSpec.normalize());
    }

    // 加入已规范化的索引描述。
    <K extends Comparable<K>> void add(ValueIndexSpec<E, K> indexSpec);

    // 构建后加入索引集合。
    default <K extends Comparable<K>> void add(IndexSpecBuilder<E, K, ?> builder) {
        add(builder.build());
    }

    // 返回全部索引描述。
    List<ValueIndexSpec<E, ?>> getIndexSpecs();
}
