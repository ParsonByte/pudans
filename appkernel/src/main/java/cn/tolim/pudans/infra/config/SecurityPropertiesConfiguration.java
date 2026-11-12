package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the nested security configuration as an injectable bean so the auth
 * filters and endpoints can read the post-login destination directly.
 */
@Configuration(proxyBeanMethods = false)
class SecurityPropertiesConfiguration {

    @Bean
    SecurityProperties securityProperties(PudansProperties pudansProperties) {
        return pudansProperties.getSecurity();
    }
}
