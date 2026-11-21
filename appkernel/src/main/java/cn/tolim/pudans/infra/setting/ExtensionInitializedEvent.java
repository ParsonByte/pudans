package cn.tolim.pudans.infra.setting;

import org.springframework.context.ApplicationEvent;


public class ExtensionInitializedEvent extends ApplicationEvent {

    // 标记扩展资源已完成初始化。
    public ExtensionInitializedEvent(Object source) {
        super(source);
    }
}
