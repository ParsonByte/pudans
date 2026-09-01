package cn.tolim.pudans.infra.setting;

import org.springframework.context.ApplicationEvent;


public class ExtensionInitializedEvent extends ApplicationEvent {

    public ExtensionInitializedEvent(Object source) {
        super(source);
    }
}
