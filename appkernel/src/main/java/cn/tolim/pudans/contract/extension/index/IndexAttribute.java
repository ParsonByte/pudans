package cn.tolim.pudans.contract.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import java.util.Set;


public interface IndexAttribute<E extends Extension, K extends Comparable<K>> {


    Class<E> getObjectType();


    Class<K> getKeyType();


    Set<K> getValues(E e);


    boolean singleValue();
}
