package cn.tolim.pudans.contract.plugin;

import org.springframework.context.ApplicationContext;


public interface SpringPlugin {


    PluginContext getPluginContext();


    ApplicationContext getApplicationContext();
}
