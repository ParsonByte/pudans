package cn.tolim.pudans.extension.index;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.extension.contract.index.ValueIndexSpec;
import java.io.Closeable;
import java.util.List;

public interface IndicesManager extends Closeable {


    <E extends Extension> void add(Class<E> type, List<ValueIndexSpec<E, ?>> specs);


    <E extends Extension> Indices<E> get(Class<E> type);


    <E extends Extension> void remove(Class<E> type);
}
