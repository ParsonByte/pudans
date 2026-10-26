package cn.tolim.pudans.plugin.event;

import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationEvent;


public class PudansPluginBeforeStopEvent extends ApplicationEvent {
    private final PluginWrapper plugin;

    public PudansPluginBeforeStopEvent(Object source, PluginWrapper plugin) {
        super(source);
        this.plugin = plugin;
    }

    public PluginWrapper getPlugin() {
        return plugin;
    }
}
