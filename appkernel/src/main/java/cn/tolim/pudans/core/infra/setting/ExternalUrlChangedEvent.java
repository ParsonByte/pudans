package cn.tolim.pudans.core.infra.setting;

import java.net.URL;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class ExternalUrlChangedEvent extends ApplicationEvent {

    @Getter
    private final URL externalUrl;

    public ExternalUrlChangedEvent(Object source, URL externalUrl) {
        super(source);
        this.externalUrl = externalUrl;
    }
}
