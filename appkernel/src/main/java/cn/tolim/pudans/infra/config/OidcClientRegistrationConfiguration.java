package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.infra.setting.SecurityParameters;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;

@Configuration
class OidcClientRegistrationConfiguration {

    @Bean
    // 从参数设置动态构建 OIDC 客户端注册仓库。
    ReactiveClientRegistrationRepository clientRegistrationRepository(
            SecurityParameters securitySettings) {
        return new SecurityClientRegistrationRepository(securitySettings);
    }
}
