package cn.tolim.pudans.plugin;

import org.springframework.context.ApplicationContext;

public interface PluginApplicationContextFactory {


    ApplicationContext create(String pluginId);
}
