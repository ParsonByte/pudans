package cn.tolim.pudans.infra.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.utils.IpAddressUtils;

@Component
@RequiredArgsConstructor
public class ClientIpAddressInitializer implements InitializingBean {

    private final PudansProperties properties;

    // 启动时注入受信代理配置，刷新客户端 IP 解析规则。
    @Override
    public void afterPropertiesSet() {
        IpAddressUtils.configure(properties.getSecurity().getTrustedProxies());
    }
}
