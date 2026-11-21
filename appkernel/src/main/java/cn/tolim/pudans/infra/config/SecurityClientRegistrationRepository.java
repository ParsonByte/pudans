package cn.tolim.pudans.infra.config;

import cn.tolim.pudans.contract.infra.ParameterSetting;
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

    SecurityClientRegistrationRepository(SecurityParameters securitySettings) {
        this.securitySettings = securitySettings;
    }

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

    private static boolean isCompleteOAuth2(ParameterSetting.OAuth2Provider provider) {
        return StringUtils.hasText(provider.getRegistrationId())
                && StringUtils.hasText(provider.getClientId())
                && StringUtils.hasText(provider.getAuthorizationUri())
                && StringUtils.hasText(provider.getTokenUri())
                && StringUtils.hasText(provider.getUserInfoUri());
    }

    private static boolean isCompleteOidc(OidcProperties.Provider provider) {
        return StringUtils.hasText(provider.getRegistrationId())
                && StringUtils.hasText(provider.getAuthorizationUri())
                && StringUtils.hasText(provider.getTokenUri())
                && StringUtils.hasText(provider.getUserInfoUri());
    }

    private static List<String> scopes(String scopes, List<String> fallback) {
        if (!StringUtils.hasText(scopes)) {
            return fallback;
        }
        return List.of(scopes.trim().split("\\s+"));
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    @Override
    public Mono<ClientRegistration> findByRegistrationId(String registrationId) {
        return Mono.justOrEmpty(registrations(securitySettings).stream()
                .filter(registration -> registration.getRegistrationId().equals(registrationId))
                .findFirst());
    }
}
