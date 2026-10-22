package cn.tolim.pudans.plugin;


public interface PluginConst {

    String PLUGIN_NAME_LABEL_NAME = "plugin.pudans.tolim.cn/plugin-name";

    String SYSTEM_PLUGIN_NAME = "system";

    String RELOAD_ANNO = "plugin.pudans.tolim.cn/reload";

    String REQUEST_TO_UNLOAD_LABEL = "plugin.pudans.tolim.cn/request-to-unload";

    String PLUGIN_PATH = "plugin.pudans.tolim.cn/plugin-path";

    String RUNTIME_MODE_ANNO = "plugin.pudans.tolim.cn/runtime-mode";

    static String assetsRoutePrefix(String pluginName) {
        return "/plugins/" + pluginName + "/assets/";
    }
}
