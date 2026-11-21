package cn.tolim.pudans.core.infra.setting;

import org.springframework.context.ApplicationEvent;


public class ExtensionInitializedEvent extends ApplicationEvent {

    public ExtensionInitializedEvent(Object source) {
        super(source);
    }
}
