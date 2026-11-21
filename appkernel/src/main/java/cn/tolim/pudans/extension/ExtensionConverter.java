package cn.tolim.pudans.extension;

import cn.tolim.pudans.extension.contract.Extension;
import cn.tolim.pudans.infra.r2dbc.store.ExtensionStore;


public interface ExtensionConverter {


    // 将扩展对象转换为持久化存储记录。
    <E extends Extension> ExtensionStore convertTo(E extension);


    // 从存储记录还原指定类型的扩展对象。
    <E extends Extension> E convertFrom(Class<E> type, ExtensionStore extensionStore);
}
