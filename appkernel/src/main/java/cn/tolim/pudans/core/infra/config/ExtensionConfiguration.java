package cn.tolim.pudans.core.infra.config;

import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.core.extension.controller.DefaultControllerManager;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ExtensionConfiguration {

    @Bean
    @ConditionalOnProperty(name = "pudans.extension.controller.disabled", havingValue = "false", matchIfMissing = true)
    DefaultControllerManager controllerManager(ExtensionClient client) {
        return new DefaultControllerManager(client);
    }
}
