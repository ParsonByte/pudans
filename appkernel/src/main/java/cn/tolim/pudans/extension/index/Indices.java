package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import java.io.Closeable;

public interface Indices<E extends Extension> extends Closeable {

    // 写入扩展的全部索引项。
    void insert(E extension);

    // 更新扩展的全部索引项。
    void update(E extension);

    // 删除扩展的全部索引项。
    void delete(E extension);


    // 按名称获取索引，缺失时报错。
    <K extends Comparable<K>> Index<E, K> getIndex(String indexName);
}
