package cn.tolim.pudans.extension.contract.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.util.Set;


public interface IndexAttribute<E extends Extension, K extends Comparable<K>> {


    // 返回该索引对应的扩展类型。
    Class<E> getObjectType();


    // 返回索引键的 Java 类型。
    Class<K> getKeyType();


    // 从扩展实例提取全部索引键。
    Set<K> getValues(E e);


    // 返回该属性是否只产生单个键。
    boolean singleValue();
}
