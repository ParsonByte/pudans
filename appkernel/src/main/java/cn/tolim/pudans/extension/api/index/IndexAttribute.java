package cn.tolim.pudans.extension.api.index;

import cn.tolim.pudans.extension.api.Extension;
import java.util.Set;


public interface IndexAttribute<E extends Extension, K extends Comparable<K>> {


    Class<E> getObjectType();


    Class<K> getKeyType();


    Set<K> getValues(E e);


    boolean singleValue();
}
