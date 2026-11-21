package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.properties.OidcProperties;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

final class SecurityClientRegistrationRepository implements ReactiveClientRegistrationRepository {

    private final SecurityParameters securitySettings;

    // 保存安全参数，注册信息按配置即时生成。
    SecurityClientRegistrationRepository(SecurityParameters securitySettings) {
        this.securitySettings = securitySettings;
    }

    // 只收集字段完整的 OAuth2 与 OIDC 提供商。
    static List<ClientRegistration> registrations(
            SecurityParameters securitySettings) {
        var registrations = new ArrayList<ClientRegistration>();
        securitySettings.oauth2Providers().forEach(provider -> {
            if (isCompleteOAuth2(provider)) {
                registrations.add(oauth2Registration(provider));
            }
        });
        securitySettings.oidcProviders().forEach(provider -> {
            if (isCompleteOidc(provider)) {
                registrations.add(oidcRegistration(provider));
            }
        });
        return registrations;
    }

    // 将 OAuth2 参数转换为 Spring 客户端注册。
    static ClientRegistration oauth2Registration(ParameterSetting.OAuth2Provider provider) {
        return registration(
                provider.getRegistrationId(),
                provider.getDisplayName(),
                provider.getClientId(),
                provider.getClientSecret(),
                provider.getAuthorizationUri(),
                provider.getTokenUri(),
                provider.getUserInfoUri(),
                defaultIfBlank(provider.getUserNameAttribute(), "id"),
                scopes(provider.getScopes(), List.of()));
    }

    // 将 OIDC 参数转换为客户端注册，缺省使用标准声明。
    static ClientRegistration oidcRegistration(OidcProperties.Provider provider) {
        return registration(
                provider.getRegistrationId(),
                provider.getDisplayName(),
                provider.getClientId(),
                provider.getClientSecret(),
                provider.getAuthorizationUri(),
                provider.getTokenUri(),
                provider.getUserInfoUri(),
                defaultIfBlank(provider.getUserNameAttribute(), "sub"),
                provider.getScopes() == null ? List.of("openid", "profile", "email") : provider.getScopes());
    }

    // 有密钥时使用基本认证，否则按公开客户端处理。
    private static ClientRegistration registration(
            String registrationId,
            String displayName,
            String clientId,
            String clientSecret,
            String authorizationUri,
            String tokenUri,
            String userInfoUri,
            String userNameAttribute,
            List<String> scopes) {
        var authenticationMethod = StringUtils.hasText(clientSecret)
                ? ClientAuthenticationMethod.CLIENT_SECRET_BASIC
                : ClientAuthenticationMethod.NONE;
        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(clientId)
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(authenticationMethod)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope(scopes)
                .authorizationUri(authorizationUri)
                .tokenUri(tokenUri)
                .userInfoUri(userInfoUri)
                .userNameAttributeName(userNameAttribute)
                .clientName(defaultIfBlank(displayName, registrationId))
                .build();
    }

    // 必要字段齐全才注册，避免产生无效客户端。
    private static boolean isCompleteOAuth2(ParameterSetting.OAuth2Provider provider) {
        return StringUtils.hasText(provider.getRegistrationId())
                && StringUtils.hasText(provider.getClientId())
                && StringUtils.hasText(provider.getAuthorizationUri())
                && StringUtils.hasText(provider.getTokenUri())
                && StringUtils.hasText(provider.getUserInfoUri());
    }

    // OIDC 至少需要标识与三个端点地址。
    private static boolean isCompleteOidc(OidcProperties.Provider provider) {
        return StringUtils.hasText(provider.getRegistrationId())
                && StringUtils.hasText(provider.getAuthorizationUri())
                && StringUtils.hasText(provider.getTokenUri())
                && StringUtils.hasText(provider.getUserInfoUri());
    }

    // 按空白拆分作用域，空值时使用回退列表。
    private static List<String> scopes(String scopes, List<String> fallback) {
        if (!StringUtils.hasText(scopes)) {
            return fallback;
        }
        return List.of(scopes.trim().split("\\s+"));
    }

    // 空白值回退到默认值，保证注册字段可用。
    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    // 按注册标识查找，未命中返回空信号。
    @Override
    public Mono<ClientRegistration> findByRegistrationId(String registrationId) {
        return Mono.justOrEmpty(registrations(securitySettings).stream()
                .filter(registration -> registration.getRegistrationId().equals(registrationId))
                .findFirst());
    }
}
