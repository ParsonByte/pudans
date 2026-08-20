package cn.tolim.pudans.extension.event;

import cn.tolim.pudans.contract.extension.Scheme;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class IndexerBuiltEvent extends ApplicationEvent {

    @Getter
    private final Scheme scheme;

    public IndexerBuiltEvent(Object source, Scheme scheme) {
        super(source);
        this.scheme = scheme;
    }
}
