package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.infra.properties.OidcProperties;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.InMemoryReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Configuration
class OidcClientRegistrationConfiguration {

    private static final ReactiveClientRegistrationRepository EMPTY_REGISTRATION_REPOSITORY =
            registrationId -> Mono.empty();

    private static ClientRegistration registration(OidcProperties.Provider provider) {
        var authenticationMethod = StringUtils.hasText(provider.getClientSecret())
                ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                : ClientAuthenticationMethod.NONE;
        return ClientRegistration.withRegistrationId(provider.getRegistrationId())
                .clientId(provider.getClientId())
                .clientSecret(provider.getClientSecret())
                .clientAuthenticationMethod(authenticationMethod)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(provider.getScopes())
                .authorizationUri(provider.getAuthorizationUri())
                .tokenUri(provider.getTokenUri())
                .userInfoUri(provider.getUserInfoUri())
                .userNameAttributeName(provider.getUserNameAttribute())
                .clientName(org.apache.commons.lang3.StringUtils.defaultIfBlank(
                        provider.getDisplayName(), provider.getRegistrationId()))
                .build();
    }

    @Bean
    ReactiveClientRegistrationRepository clientRegistrationRepository(OidcProperties properties) {
        List<ClientRegistration> registrations = properties.getProviders().stream()
                .filter(provider -> StringUtils.hasText(provider.getRegistrationId()))
                .filter(provider -> StringUtils.hasText(provider.getAuthorizationUri()))
                .filter(provider -> StringUtils.hasText(provider.getTokenUri()))
                .filter(provider -> StringUtils.hasText(provider.getUserInfoUri()))
                .map(OidcClientRegistrationConfiguration::registration)
                .toList();
        if (registrations.isEmpty()) {
            log.info("未配置 OIDC 客户端注册，OAuth2 登录端点保持不可用。");
            return EMPTY_REGISTRATION_REPOSITORY;
        }
        return new InMemoryReactiveClientRegistrationRepository(registrations);
    }
}
