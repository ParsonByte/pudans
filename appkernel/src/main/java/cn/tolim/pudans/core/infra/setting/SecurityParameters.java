package cn.tolim.pudans.core.infra.setting;

import cn.tolim.pudans.contract.infra.ParameterSetting;
import cn.tolim.pudans.core.infra.properties.FeishuProperties;
import cn.tolim.pudans.core.infra.properties.OidcProperties;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityParameters {

    private static final String DEFAULT_TOTP_ISSUER = "pudans";

    private static final List<String> REQUIRED_FEISHU_SCOPES = List.of(
            "contact:user.base:readonly",
            "contact:user.email:readonly",
            "contact:user.phone:readonly");

    private final ParameterSettingFetcher fetcher;

    private final ParameterSettingSecretCipher secretCipher;

    private final OidcProperties oidcProperties;

    private final FeishuProperties feishuProperties;

    private volatile ParameterSetting.Session session = new ParameterSetting.Session();

    private volatile ParameterSetting.Authentication authentication = new ParameterSetting.Authentication();

    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void initialize(ExtensionInitializedEvent ignored) {
        refresh().subscribe(null, error -> log.warn("无法加载安全参数设置", error));
    }

    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady(ApplicationReadyEvent ignored) {
        refresh().subscribe(null, error -> log.warn("无法刷新安全参数设置", error));
    }

    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    void onParameterSettingChanged(ParameterSettingChangedEvent ignored) {
        refresh().subscribe(null, error -> log.warn("无法刷新安全参数设置", error));
    }

    public Mono<Void> refresh() {
        return Mono.zip(
                        fetcher.fetch(ParameterSetting.Session.GROUP, ParameterSetting.Session.class)
                                .defaultIfEmpty(new ParameterSetting.Session()),
                        fetcher.fetch(ParameterSetting.Authentication.GROUP, ParameterSetting.Authentication.class)
                                .defaultIfEmpty(new ParameterSetting.Authentication()))
                .doOnNext(settings -> {
                    session = settings.getT1();
                    var authenticationSettings = settings.getT2();
                    AuthenticationSettingSecrets.decrypt(authenticationSettings, secretCipher);
                    authentication = authenticationSettings;
                })
                .then();
    }

    public boolean isRsaLoginEncryptionEnabled() {
        return !Boolean.FALSE.equals(session.getRsaLoginEncryptionEnabled());
    }

    public boolean isArgon2PasswordStorageEnabled() {
        return !Boolean.FALSE.equals(session.getArgon2PasswordStorageEnabled());
    }

    public boolean isTotpEnabled() {
        var totp = authentication.getTotp();
        return totp == null || !Boolean.FALSE.equals(totp.getEnabled());
    }

    public String totpIssuer() {
        var totp = authentication.getTotp();
        return totp == null || !StringUtils.hasText(totp.getIssuer())
                ? DEFAULT_TOTP_ISSUER
                : totp.getIssuer();
    }

    public List<ParameterSetting.OAuth2Provider> oauth2Providers() {
        var oauth2 = authentication.getOauth2();
        if (oauth2 == null || oauth2.getProviders() == null) {
            return List.of();
        }
        return oauth2.getProviders().stream()
                .filter(Objects::nonNull)
                .filter(provider -> !Boolean.FALSE.equals(provider.getEnabled()))
                .filter(provider -> StringUtils.hasText(provider.getRegistrationId()))
                .filter(provider -> StringUtils.hasText(provider.getClientId()))
                .filter(provider -> StringUtils.hasText(provider.getAuthorizationUri()))
                .filter(provider -> StringUtils.hasText(provider.getTokenUri()))
                .filter(provider -> StringUtils.hasText(provider.getUserInfoUri()))
                .toList();
    }

    public List<OidcProperties.Provider> oidcProviders() {
        var oidc = authentication.getOidc();
        if (oidc != null && oidc.getProviders() != null && !oidc.getProviders().isEmpty()) {
            return oidc.getProviders().stream()
                    .filter(Objects::nonNull)
                    .filter(provider -> !Boolean.FALSE.equals(provider.getEnabled()))
                    .filter(provider -> StringUtils.hasText(provider.getRegistrationId()))
                    .map(SecurityParameters::toOidcProvider)
                    .filter(provider -> StringUtils.hasText(provider.getAuthorizationUri()))
                    .filter(provider -> StringUtils.hasText(provider.getTokenUri()))
                    .filter(provider -> StringUtils.hasText(provider.getUserInfoUri()))
                    .toList();
        }
        return oidcProperties.getProviders().stream()
                .filter(OidcProperties.Provider::isEnabled)
                .toList();
    }

    public FeishuSettings feishu() {
        var configured = authentication.getFeishu();
        var enabled = configured != null && configured.getEnabled() != null
                ? configured.getEnabled()
                : feishuProperties.isEnabled();
        return new FeishuSettings(
                enabled,
                firstNonBlank(configured == null ? null : configured.getAppId(), feishuProperties.getAppId()),
                firstNonBlank(
                        configured == null ? null : configured.getAppSecret(),
                        feishuProperties.getAppSecret()),
                firstNonBlank(
                        configured == null ? null : configured.getRedirectUri(),
                        feishuProperties.getRedirectUri()),
                firstNonBlank(
                        configured == null ? null : configured.getAuthorizationUri(),
                        feishuProperties.getAuthorizationUri()),
                withRequiredFeishuScopes(firstNonBlank(
                        configured == null ? null : configured.getScope(), feishuProperties.getScope())),
                configured != null && configured.getSyncIntervalSeconds() != null
                        ? configured.getSyncIntervalSeconds()
                        : feishuProperties.getSyncIntervalSeconds(),
                firstNonBlank(
                        configured == null ? null : configured.getAlertChatId(),
                        feishuProperties.getAlertChatId()));
    }

    public boolean isPatEnabled() {
        var pat = authentication.getPat();
        return pat == null || !Boolean.FALSE.equals(pat.getEnabled());
    }

    public int patMaxLifetimeDays() {
        var pat = authentication.getPat();
        return pat == null || pat.getMaxLifetimeDays() == null || pat.getMaxLifetimeDays() < 0
                ? 0
                : pat.getMaxLifetimeDays();
    }

    private static OidcProperties.Provider toOidcProvider(ParameterSetting.OidcProvider provider) {
        var result = new OidcProperties.Provider();
        result.setRegistrationId(provider.getRegistrationId());
        result.setDisplayName(provider.getDisplayName());
        result.setIssuerUri(provider.getIssuerUri());
        result.setAuthorizationUri(provider.getAuthorizationUri());
        result.setTokenUri(provider.getTokenUri());
        result.setUserInfoUri(provider.getUserInfoUri());
        result.setClientId(provider.getClientId());
        result.setClientSecret(provider.getClientSecret());
        result.setScopes(parseScopes(provider.getScopes()));
        if (StringUtils.hasText(provider.getUserNameAttribute())) {
            result.setUserNameAttribute(provider.getUserNameAttribute());
        }
        return result;
    }

    private static List<String> parseScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return List.of("openid", "profile", "email");
        }
        return List.of(scopes.trim().split("\\s+"));
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private static String withRequiredFeishuScopes(String scope) {
        var scopes = new LinkedHashSet<String>();
        if (StringUtils.hasText(scope)) {
            scopes.addAll(List.of(scope.trim().split("\\s+")));
        }
        scopes.addAll(REQUIRED_FEISHU_SCOPES);
        return String.join(" ", scopes);
    }

    public record FeishuSettings(
            boolean enabled,
            String appId,
            String appSecret,
            String redirectUri,
            String authorizationUri,
            String scope,
            long syncIntervalSeconds,
            String alertChatId) {

        public boolean isConfigured() {
            return enabled && StringUtils.hasText(appId) && StringUtils.hasText(appSecret);
        }
    }
}
