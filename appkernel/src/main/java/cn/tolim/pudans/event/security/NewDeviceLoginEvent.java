package cn.tolim.pudans.event.security;

import cn.tolim.pudans.security.contract.Device;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class NewDeviceLoginEvent extends ApplicationEvent {
    private final Device device;

    // 携带触发安全告警的新设备信息。
    public NewDeviceLoginEvent(Object source, Device device) {
        super(source);
        this.device = device;
    }
}
