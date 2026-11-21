package cn.tolim.pudans.core.extension;

import cn.tolim.pudans.contract.extension.Extension;
import cn.tolim.pudans.core.infra.r2dbc.store.ExtensionStore;


public interface ExtensionConverter {


    <E extends Extension> ExtensionStore convertTo(E extension);


    <E extends Extension> E convertFrom(Class<E> type, ExtensionStore extensionStore);
}
