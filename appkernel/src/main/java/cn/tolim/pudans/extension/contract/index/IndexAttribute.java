package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.util.Set;


public interface IndexAttribute<E extends Extension, K extends Comparable<K>> {


    Class<E> getObjectType();


    Class<K> getKeyType();


    Set<K> getValues(E e);


    boolean singleValue();
}
