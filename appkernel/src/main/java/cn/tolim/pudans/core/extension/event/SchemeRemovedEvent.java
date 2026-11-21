package cn.tolim.pudans.core.extension.event;

import cn.tolim.pudans.contract.extension.Scheme;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class SchemeRemovedEvent extends ApplicationEvent {

    @Getter
    private final Scheme scheme;

    public SchemeRemovedEvent(Object source, Scheme scheme) {
        super(source);
        this.scheme = scheme;
    }
}
