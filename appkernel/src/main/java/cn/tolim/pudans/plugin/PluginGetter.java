package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.core.extension.Plugin;


@FunctionalInterface
public interface PluginGetter {


    Plugin getPlugin(String name);
}
