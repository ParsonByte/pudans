package cn.tolim.pudans.event.security;

import cn.tolim.pudans.security.api.Device;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NewDeviceLoginEvent extends ApplicationEvent {
    private final Device device;

    public NewDeviceLoginEvent(Object source, Device device) {
        super(source);
        this.device = device;
    }
}
