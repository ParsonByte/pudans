package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.index.ValueIndexSpec;
import java.io.Closeable;
import java.util.List;

public interface IndicesManager extends Closeable {


    // 为类型注册索引描述。
    <E extends Extension> void add(Class<E> type, List<ValueIndexSpec<E, ?>> specs);


    // 获取类型的索引集合。
    <E extends Extension> Indices<E> get(Class<E> type);


    // 移除类型的索引集合。
    <E extends Extension> void remove(Class<E> type);
}
