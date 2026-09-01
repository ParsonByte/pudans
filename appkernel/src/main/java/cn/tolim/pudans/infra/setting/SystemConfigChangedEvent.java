package cn.tolim.pudans.infra.setting;

import java.util.Map;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;


public class SystemConfigChangedEvent extends ApplicationEvent {


    @Getter
    private final Map<String, String> oldData;


    @Getter
    private final Map<String, String> newData;

    public SystemConfigChangedEvent(Object source, Map<String, String> oldData, Map<String, String> newData) {
        super(source);
        this.oldData = Map.copyOf(oldData);
        this.newData = Map.copyOf(newData);
    }
}
