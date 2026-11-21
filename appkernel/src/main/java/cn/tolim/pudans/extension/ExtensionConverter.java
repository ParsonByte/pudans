package cn.tolim.pudans.extension;

import cn.tolim.pudans.extension.api.Extension;
import cn.tolim.pudans.infra.r2dbc.store.ExtensionStore;


public interface ExtensionConverter {


    <E extends Extension> ExtensionStore convertTo(E extension);


    <E extends Extension> E convertFrom(Class<E> type, ExtensionStore extensionStore);
}
