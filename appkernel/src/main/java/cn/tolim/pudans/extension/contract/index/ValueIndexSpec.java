package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;


public interface ValueIndexSpec<E extends Extension, K extends Comparable<K>> {


    // 返回索引在类型内的唯一名称。
    String getName();


    // 返回索引是否唯一。
    boolean isUnique();


    // 返回索引是否允许空值。
    boolean isNullable();


    // 返回索引键类型。
    Class<K> getKeyType();
}
