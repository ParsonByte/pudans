package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.infra.setting.SecurityParameterSettingService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;

@Configuration
class OidcClientRegistrationConfiguration {

    @Bean
    ReactiveClientRegistrationRepository clientRegistrationRepository(
            SecurityParameterSettingService securitySettings) {
        return new SecurityClientRegistrationRepository(securitySettings);
    }
}
