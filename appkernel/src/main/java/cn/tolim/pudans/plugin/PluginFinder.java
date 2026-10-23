package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.core.extension.Plugin;
import java.nio.file.Path;


public interface PluginFinder {


    Plugin find(Path pluginPath);
}
