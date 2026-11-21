package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;


public interface ValueIndexSpec<E extends Extension, K extends Comparable<K>> {


    String getName();


    boolean isUnique();


    boolean isNullable();


    Class<K> getKeyType();
}
