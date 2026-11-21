package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.io.Closeable;


public interface Index<E extends Extension, K extends Comparable<K>> extends Closeable {


    // 返回索引在类型内的唯一名称。
    String getName();


    // 返回索引键类型。
    Class<K> getKeyType();


    // 默认索引不要求唯一。
    default boolean isUnique() {
        return false;
    }


    // 准备插入事务操作。
    TransactionalOperation prepareInsert(E extension);


    // 准备更新事务操作。
    TransactionalOperation prepareUpdate(E newExtension);


    // 按主键准备删除事务操作。
    TransactionalOperation prepareDelete(String primaryKey);
}
