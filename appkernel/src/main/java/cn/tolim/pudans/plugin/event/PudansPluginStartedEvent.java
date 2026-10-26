package cn.tolim.pudans.plugin.event;

import lombok.Getter;
import org.pf4j.PluginWrapper;
import org.springframework.context.ApplicationEvent;
import org.springframework.util.Assert;


@Getter
public class PudansPluginStartedEvent extends ApplicationEvent {

    private final PluginWrapper plugin;

    public PudansPluginStartedEvent(Object source, PluginWrapper plugin) {
        super(source);
        Assert.notNull(plugin, "Plugin must not be null.");
        this.plugin = plugin;
    }
}
