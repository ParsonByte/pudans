package cn.tolim.pudans.infra.setting;

import java.net.URL;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class ExternalUrlChangedEvent extends ApplicationEvent {

    @Getter
    private final URL externalUrl;

    // 携带新的外部访问地址，通知相关组件刷新。
    public ExternalUrlChangedEvent(Object source, URL externalUrl) {
        super(source);
        this.externalUrl = externalUrl;
    }
}
