package cn.tolim.pudans.infra.setting;

import cn.tolim.pudans.infra.contract.ParameterSetting;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import cn.tolim.pudans.infra.properties.OidcProperties;
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

    // 扩展初始化后加载安全参数，失败时保留默认值。
    @EventListener
    @Order(Ordered.HIGHEST_PRECEDENCE)
    void initialize(ExtensionInitializedEvent ignored) {
        refresh().subscribe(null, error -> log.warn("无法加载安全参数设置", error));
    }

    // 应用就绪后再刷新一次，覆盖延迟写入的配置。
    @EventListener(ApplicationReadyEvent.class)
    void onApplicationReady(ApplicationReadyEvent ignored) {
        refresh().subscribe(null, error -> log.warn("无法刷新安全参数设置", error));
    }

    // 配置变更后刷新缓存的安全参数。
    @EventListener
    @Order(Ordered.LOWEST_PRECEDENCE)
    void onParameterSettingChanged(ParameterSettingChangedEvent ignored) {
        refresh().subscribe(null, error -> log.warn("无法刷新安全参数设置", error));
    }

    // 读取并解密会话与认证设置，更新内存快照。
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

    // 默认启用登录非对称加密，除非显式关闭。
    public boolean isRsaLoginEncryptionEnabled() {
        return !Boolean.FALSE.equals(session.getRsaLoginEncryptionEnabled());
    }

    // 默认启用强密码哈希存储，除非显式关闭。
    public boolean isArgon2PasswordStorageEnabled() {
        return !Boolean.FALSE.equals(session.getArgon2PasswordStorageEnabled());
    }

    // 默认启用动态口令，除非显式关闭。
    public boolean isTotpEnabled() {
        var totp = authentication.getTotp();
        return totp == null || !Boolean.FALSE.equals(totp.getEnabled());
    }

    // 返回动态口令发行方，未配置时使用默认名称。
    public String totpIssuer() {
        var totp = authentication.getTotp();
        return totp == null || !StringUtils.hasText(totp.getIssuer())
                ? DEFAULT_TOTP_ISSUER
                : totp.getIssuer();
    }

    // 过滤出字段完整且已启用的第三方登录提供者。
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

    // 优先使用参数设置，缺失时回退到属性配置。
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

    // 合并参数设置与属性中的飞书配置。
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

    // 默认启用个人访问令牌，除非显式关闭。
    public boolean isPatEnabled() {
        var pat = authentication.getPat();
        return pat == null || !Boolean.FALSE.equals(pat.getEnabled());
    }

    // 返回令牌最大有效期，未配置时表示不限制。
    public int patMaxLifetimeDays() {
        var pat = authentication.getPat();
        return pat == null || pat.getMaxLifetimeDays() == null || pat.getMaxLifetimeDays() < 0
                ? 0
                : pat.getMaxLifetimeDays();
    }

    // 将参数设置中的提供者转换为运行时模型。
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

    // 按空白拆分授权范围，缺失时给出默认范围。
    private static List<String> parseScopes(String scopes) {
        if (!StringUtils.hasText(scopes)) {
            return List.of("openid", "profile", "email");
        }
        return List.of(scopes.trim().split("\\s+"));
    }

    // 优先返回非空配置，否则使用回退值。
    private static String firstNonBlank(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    // 合并飞书必需权限范围，避免用户漏配。
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

        // 判断飞书是否启用且应用凭据完整。
        public boolean isConfigured() {
            return enabled && StringUtils.hasText(appId) && StringUtils.hasText(appSecret);
        }
    }
}
