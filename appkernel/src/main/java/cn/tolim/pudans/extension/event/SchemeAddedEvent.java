package cn.tolim.pudans.extension.event;

import cn.tolim.pudans.extension.contract.Scheme;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class SchemeAddedEvent extends ApplicationEvent {

    @Getter
    private final Scheme scheme;

    // 携带新增方案发布应用事件。
    public SchemeAddedEvent(Object source, Scheme scheme) {
        super(source);
        this.scheme = scheme;
    }
}
