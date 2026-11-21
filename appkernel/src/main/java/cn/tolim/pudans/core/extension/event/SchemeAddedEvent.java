package cn.tolim.pudans.core.extension.event;

import cn.tolim.pudans.contract.extension.Scheme;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class SchemeAddedEvent extends ApplicationEvent {

    @Getter
    private final Scheme scheme;

    public SchemeAddedEvent(Object source, Scheme scheme) {
        super(source);
        this.scheme = scheme;
    }
}
