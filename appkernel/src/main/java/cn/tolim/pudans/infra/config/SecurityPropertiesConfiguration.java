package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;

// 将嵌套的安全配置暴露为可注入的 Bean，便于认证过滤器和端点直接读取登录后跳转地址。
@Configuration(proxyBeanMethods = false)
class SecurityPropertiesConfiguration {

    // 注册配置属性校验器，让外部 URL 等自定义规则在启动时生效。
    @Bean
    static Validator configurationPropertiesValidator() {
        return new PudansProperties();
    }

    @Bean
    // 以 Bean 形式暴露安全配置，避免调用方感知嵌套结构。
    SecurityProperties securityProperties(PudansProperties pudansProperties) {
        return pudansProperties.getSecurity();
    }
}
