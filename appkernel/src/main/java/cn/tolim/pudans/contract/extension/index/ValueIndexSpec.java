package cn.tolim.pudans.contract.extension.index;

import cn.tolim.pudans.contract.extension.Extension;


public interface ValueIndexSpec<E extends Extension, K extends Comparable<K>> {


    String getName();


    boolean isUnique();


    boolean isNullable();


    Class<K> getKeyType();
}
