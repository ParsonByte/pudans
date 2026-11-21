package cn.tolim.pudans.core.extension.index;

import cn.tolim.pudans.contract.extension.Extension;
import java.io.Closeable;

public interface Indices<E extends Extension> extends Closeable {

    void insert(E extension);

    void update(E extension);

    void delete(E extension);


    <K extends Comparable<K>> Index<E, K> getIndex(String indexName);
}
