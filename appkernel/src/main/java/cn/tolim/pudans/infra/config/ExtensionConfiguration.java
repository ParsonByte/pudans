package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.extension.contract.ExtensionClient;
import cn.tolim.pudans.extension.controller.DefaultControllerManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExtensionConfiguration {

    @Bean
    @ConditionalOnProperty(name = "pudans.extension.controller.disabled", havingValue = "false", matchIfMissing = true)
    // 按配置决定是否启用扩展控制器管理器。
    DefaultControllerManager controllerManager(ExtensionClient client) {
        return new DefaultControllerManager(client);
    }
}
