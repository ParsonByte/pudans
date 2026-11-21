package cn.tolim.pudans.core.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import java.io.Closeable;


public interface Index<E extends Extension, K extends Comparable<K>> extends Closeable {


    String getName();


    Class<K> getKeyType();


    default boolean isUnique() {
        return false;
    }


    TransactionalOperation prepareInsert(E extension);


    TransactionalOperation prepareUpdate(E newExtension);


    TransactionalOperation prepareDelete(String primaryKey);
}
