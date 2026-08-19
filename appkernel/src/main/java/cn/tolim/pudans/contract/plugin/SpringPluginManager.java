package cn.tolim.pudans.contract.plugin;

import java.util.List;
import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationContext;


public interface SpringPluginManager extends PluginManager {


    ApplicationContext getRootContext();


    ApplicationContext getSharedContext();


    List<PluginWrapper> getDependents(String pluginId);


    @Override
    List<PluginWrapper> getStartedPlugins();


    List<PluginWrapper> startedPlugins();
}
