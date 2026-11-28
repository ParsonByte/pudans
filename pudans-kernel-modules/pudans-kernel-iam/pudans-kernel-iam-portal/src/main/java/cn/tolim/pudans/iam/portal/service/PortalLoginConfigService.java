package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.iam.communal.entity.authn.IdentityProviderEntity;
import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.repository.authentication.IdentityProviderRepository;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.core.setting.MessageSettingConstants;
import cn.tolim.pudans.iam.core.setting.SecuritySettingConstants;
import cn.tolim.pudans.iam.portal.config.PortalProperties;
import cn.tolim.pudans.iam.portal.constant.PortalConstants;
import cn.tolim.pudans.iam.portal.dto.PortalBrandingView;
import cn.tolim.pudans.iam.portal.dto.PortalIdentityProviderView;
import cn.tolim.pudans.iam.portal.dto.PortalLoginConfigResponse;
import cn.tolim.pudans.iam.portal.dto.PortalLoginMethodView;
import cn.tolim.pudans.iam.portal.dto.PortalLogoutUrlResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class PortalLoginConfigService {

    private final SettingRepository settingRepository;
    private final IdentityProviderRepository identityProviderRepository;
    private final PortalUrlResolver portalUrlResolver;
    private final PortalProperties portalProperties;

    public PortalLoginConfigService(SettingRepository settingRepository,
                                    IdentityProviderRepository identityProviderRepository,
                                    PortalUrlResolver portalUrlResolver,
                                    PortalProperties portalProperties) {
        this.settingRepository = settingRepository;
        this.identityProviderRepository = identityProviderRepository;
        this.portalUrlResolver = portalUrlResolver;
        this.portalProperties = portalProperties;
    }

    // Builds the dedicated login page configuration
    // Parameter fallbackBaseUrl: String request derived base URL
    // Returns: PortalLoginConfigResponse
    public PortalLoginConfigResponse getLoginConfig(String fallbackBaseUrl) {
        PortalLoginConfigResponse response = new PortalLoginConfigResponse();
        response.setHomeUrl(portalUrlResolver.resolveHomeUrl(fallbackBaseUrl));
        response.setLoginUrl(portalUrlResolver.resolveLoginUrl(fallbackBaseUrl));
        response.setLogoutUrl(portalUrlResolver.resolveLogoutUrl(fallbackBaseUrl));
        response.setBranding(buildBranding());
        response.setMethods(buildLoginMethods());
        response.setIdentityProviders(buildIdentityProviders());
        response.setCaptchaEnabled(readBoolean(PortalConstants.SETTING_LOGIN_CAPTCHA_ENABLED, false));
        response.setMfaRequired(readBoolean(PortalConstants.SETTING_LOGIN_MFA_REQUIRED, false));
        response.setRememberMeEnabled(readBoolean(PortalConstants.SETTING_LOGIN_REMEMBER_ME_ENABLED, true));
        response.setSessionValidTimeSeconds(readLong(
                SecuritySettingConstants.SECURITY_BASIC_SESSION_VALID_TIME,
                PortalConstants.DEFAULT_SESSION_VALID_TIME_SECONDS));
        return response;
    }

    // Builds the global logout URL information
    // Parameter fallbackBaseUrl: String request derived base URL
    // Returns: PortalLogoutUrlResponse
    public PortalLogoutUrlResponse getLogoutUrl(String fallbackBaseUrl) {
        PortalLogoutUrlResponse response = new PortalLogoutUrlResponse();
        response.setLogoutUrl(portalUrlResolver.resolveLogoutUrl(fallbackBaseUrl));
        response.setLoginUrl(portalUrlResolver.resolveLoginUrl(fallbackBaseUrl));
        response.setHomeUrl(portalUrlResolver.resolveHomeUrl(fallbackBaseUrl));
        return response;
    }

    private PortalBrandingView buildBranding() {
        PortalBrandingView branding = new PortalBrandingView();
        branding.setTitle(firstNonBlank(readSetting(PortalConstants.SETTING_LOGIN_TITLE),
                portalProperties.getBrandingTitle()));
        branding.setLogoUrl(readSetting(PortalConstants.SETTING_LOGIN_LOGO_URL));
        branding.setFaviconUrl(readSetting(PortalConstants.SETTING_LOGIN_FAVICON_URL));
        branding.setBackgroundUrl(readSetting(PortalConstants.SETTING_LOGIN_BACKGROUND_URL));
        return branding;
    }

    private List<PortalLoginMethodView> buildLoginMethods() {
        List<PortalLoginMethodView> methods = new ArrayList<>();
        methods.add(loginMethod(PortalConstants.METHOD_PASSWORD, "用户名密码",
                PortalConstants.CATEGORY_PASSWORD,
                readBoolean(PortalConstants.SETTING_LOGIN_PASSWORD_ENABLED, true), 10));
        methods.add(loginMethod(PortalConstants.METHOD_SMS, "短信验证码",
                PortalConstants.CATEGORY_OTP,
                readBoolean(PortalConstants.SETTING_LOGIN_SMS_ENABLED, smsConfigured()), 20));
        methods.add(loginMethod(PortalConstants.METHOD_MAIL, "邮件验证码",
                PortalConstants.CATEGORY_OTP,
                readBoolean(PortalConstants.SETTING_LOGIN_MAIL_ENABLED, mailConfigured()), 30));
        methods.add(loginMethod(PortalConstants.METHOD_TOTP, "动态口令",
                PortalConstants.CATEGORY_MFA,
                readBoolean(PortalConstants.SETTING_LOGIN_TOTP_ENABLED, false), 40));
        methods.add(loginMethod(PortalConstants.METHOD_WEBAUTHN, "WebAuthn 安全密钥",
                PortalConstants.CATEGORY_MFA,
                readBoolean(PortalConstants.SETTING_LOGIN_WEBAUTHN_ENABLED, false), 50));
        return methods;
    }

    private List<PortalIdentityProviderView> buildIdentityProviders() {
        List<IdentityProviderEntity> providers =
                identityProviderRepository.findByEnabledIsTrueAndDisplayedIsTrue();
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        List<PortalIdentityProviderView> views = new ArrayList<>();
        for (IdentityProviderEntity provider : providers) {
            if (provider == null || StringUtils.isBlank(provider.getCode())) {
                continue;
            }
            PortalIdentityProviderView view = new PortalIdentityProviderView();
            view.setCode(provider.getCode());
            view.setName(provider.getName());
            view.setType(provider.getType());
            view.setCategory(provider.getCategory());
            views.add(view);
        }
        views.sort(Comparator.comparing(
                        (PortalIdentityProviderView view) -> StringUtils.defaultString(view.getCategory()))
                .thenComparing(
                        (PortalIdentityProviderView view) -> StringUtils.defaultString(view.getName()),
                        String.CASE_INSENSITIVE_ORDER));
        return views;
    }

    private PortalLoginMethodView loginMethod(String code, String name, String category, boolean enabled,
                                              int order) {
        PortalLoginMethodView method = new PortalLoginMethodView();
        method.setCode(code);
        method.setName(name);
        method.setCategory(category);
        method.setEnabled(enabled);
        method.setOrder(order);
        return method;
    }

    private boolean smsConfigured() {
        return StringUtils.isNotBlank(readSetting(MessageSettingConstants.MESSAGE_SMS_PROVIDER));
    }

    private boolean mailConfigured() {
        return StringUtils.isNotBlank(readSetting(MessageSettingConstants.MESSAGE_PROVIDER_EMAIL));
    }

    private String readSetting(String name) {
        SettingEntity setting = settingRepository.findByName(name);
        return setting == null ? null : setting.getValue();
    }

    private boolean readBoolean(String name, boolean defaultValue) {
        String value = readSetting(name);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized)
                || "on".equals(normalized) || "enabled".equals(normalized);
    }

    private long readLong(String name, long defaultValue) {
        String value = readSetting(name);
        if (StringUtils.isBlank(value)) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (NumberFormatException exception) {
            return defaultValue;
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (StringUtils.isNotBlank(first)) {
            return first;
        }
        return StringUtils.isNotBlank(second) ? second : null;
    }
}