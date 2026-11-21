package cn.tolim.pudans.extension.event;

import cn.tolim.pudans.extension.contract.Scheme;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class SchemeRemovedEvent extends ApplicationEvent {

    @Getter
    private final Scheme scheme;

    // 携带移除方案发布应用事件。
    public SchemeRemovedEvent(Object source, Scheme scheme) {
        super(source);
        this.scheme = scheme;
    }
}
