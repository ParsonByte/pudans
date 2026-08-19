package cn.tolim.pudans.contract.plugin.event;

import org.springframework.context.ApplicationEvent;


public class PluginStartedEvent extends ApplicationEvent {

    public PluginStartedEvent(Object source) {
        super(source);
    }
}
