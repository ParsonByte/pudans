package cn.tolim.pudans.extension.event;

import cn.tolim.pudans.extension.api.Scheme;
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
