package cn.tolim.pudans.security.authentication.device;

import cn.tolim.pudans.contract.security.device.DeviceService;
import cn.tolim.pudans.security.authentication.SecurityConfigurer;
import lombok.RequiredArgsConstructor;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DeviceSecurityConfigurer implements SecurityConfigurer {

    private final DeviceService deviceService;

    @Override
    public void configure(ServerHttpSecurity http) {
        var filter = new DeviceSessionFilter(deviceService);
        http.addFilterBefore(filter, SecurityWebFiltersOrder.AUTHENTICATION);
    }
}
