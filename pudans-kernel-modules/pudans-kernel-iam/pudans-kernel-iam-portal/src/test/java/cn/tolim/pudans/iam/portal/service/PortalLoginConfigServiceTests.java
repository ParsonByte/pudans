package cn.tolim.pudans.iam.portal.service;

import cn.tolim.pudans.iam.communal.entity.authn.IdentityProviderEntity;
import cn.tolim.pudans.iam.communal.entity.setting.SettingEntity;
import cn.tolim.pudans.iam.communal.repository.authentication.IdentityProviderRepository;
import cn.tolim.pudans.iam.communal.repository.setting.SettingRepository;
import cn.tolim.pudans.iam.infra.autoconfiguration.SupportProperties;
import cn.tolim.pudans.iam.portal.config.PortalProperties;
import cn.tolim.pudans.iam.portal.constant.PortalConstants;
import cn.tolim.pudans.iam.portal.dto.PortalLoginConfigResponse;
import cn.tolim.pudans.iam.portal.dto.PortalLoginMethodView;
import cn.tolim.pudans.iam.portal.dto.PortalLogoutUrlResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PortalLoginConfigServiceTests {

    private final SettingRepository settingRepository = mock(SettingRepository.class);
    private final IdentityProviderRepository identityProviderRepository =
            mock(IdentityProviderRepository.class);
    private final PortalProperties portalProperties = new PortalProperties();
    private final Map<String, String> settings = new HashMap<>();
    private final PortalLoginConfigService service = new PortalLoginConfigService(settingRepository,
            identityProviderRepository,
            new PortalUrlResolver(portalProperties, supportProvider()), portalProperties);

    {
        portalProperties.setHomeBaseUrl("https://home.test/");
        when(settingRepository.findByName(anyString())).thenAnswer(invocation -> {
            String value = settings.get(invocation.getArgument(0, String.class));
            if (value == null) {
                return null;
            }
            SettingEntity setting = new SettingEntity();
            setting.setName(invocation.getArgument(0, String.class));
            setting.setValue(value);
            return setting;
        });
    }

    @Test
    void returnsDefaultLoginConfig() {
        PortalLoginConfigResponse response = service.getLoginConfig(null);

        assertThat(response.getHomeUrl()).isEqualTo("https://home.test");
        assertThat(response.getLoginUrl()).isEqualTo("https://home.test/login");
        assertThat(response.getLogoutUrl()).isEqualTo("https://home.test/logout");
        assertThat(response.getBranding().getTitle()).isEqualTo("PUDANS");
        assertThat(response.getMethods()).hasSize(5);
        assertThat(method(response, PortalConstants.METHOD_PASSWORD).getEnabled()).isTrue();
        assertThat(method(response, PortalConstants.METHOD_SMS).getEnabled()).isFalse();
        assertThat(method(response, PortalConstants.METHOD_MAIL).getEnabled()).isFalse();
        assertThat(response.getCaptchaEnabled()).isFalse();
        assertThat(response.getMfaRequired()).isFalse();
        assertThat(response.getRememberMeEnabled()).isTrue();
        assertThat(response.getSessionValidTimeSeconds())
                .isEqualTo(PortalConstants.DEFAULT_SESSION_VALID_TIME_SECONDS);
    }

    @Test
    void appliesSettingsOverrides() {
        settings.put(PortalConstants.SETTING_LOGIN_TITLE, "Acme Identity");
        settings.put(PortalConstants.SETTING_LOGIN_LOGO_URL, "https://cdn.test/logo.svg");
        settings.put(PortalConstants.SETTING_LOGIN_SMS_ENABLED, "true");
        settings.put(PortalConstants.SETTING_LOGIN_MAIL_ENABLED, "on");
        settings.put(PortalConstants.SETTING_LOGIN_TOTP_ENABLED, "yes");
        settings.put(PortalConstants.SETTING_LOGIN_CAPTCHA_ENABLED, "1");
        settings.put(PortalConstants.SETTING_LOGIN_MFA_REQUIRED, "true");
        settings.put(PortalConstants.SETTING_LOGIN_REMEMBER_ME_ENABLED, "false");
        settings.put("security.basic.session_valid_time", "3600");

        PortalLoginConfigResponse response = service.getLoginConfig(null);

        assertThat(response.getBranding().getTitle()).isEqualTo("Acme Identity");
        assertThat(response.getBranding().getLogoUrl()).isEqualTo("https://cdn.test/logo.svg");
        assertThat(method(response, PortalConstants.METHOD_SMS).getEnabled()).isTrue();
        assertThat(method(response, PortalConstants.METHOD_MAIL).getEnabled()).isTrue();
        assertThat(method(response, PortalConstants.METHOD_TOTP).getEnabled()).isTrue();
        assertThat(response.getCaptchaEnabled()).isTrue();
        assertThat(response.getMfaRequired()).isTrue();
        assertThat(response.getRememberMeEnabled()).isFalse();
        assertThat(response.getSessionValidTimeSeconds()).isEqualTo(3600L);
    }

    @Test
    void mapsDisplayedIdentityProviders() {
        IdentityProviderEntity dingTalk = new IdentityProviderEntity();
        dingTalk.setId("idp-1");
        dingTalk.setCode("dingtalk");
        dingTalk.setName("DingTalk");
        dingTalk.setType("dingtalk_oauth");
        dingTalk.setCategory("enterprise");
        dingTalk.setEnabled(Boolean.TRUE);
        dingTalk.setDisplayed(Boolean.TRUE);
        when(identityProviderRepository.findByEnabledIsTrueAndDisplayedIsTrue())
                .thenReturn(List.of(dingTalk));

        PortalLoginConfigResponse response = service.getLoginConfig("http://localhost:8081");

        assertThat(response.getIdentityProviders()).hasSize(1);
        assertThat(response.getIdentityProviders().get(0).getCode()).isEqualTo("dingtalk");
        assertThat(response.getIdentityProviders().get(0).getCategory()).isEqualTo("enterprise");
        assertThat(response.getIdentityProviders().get(0).getType()).isEqualTo("dingtalk_oauth");
    }

    @Test
    void usesSupportPortalBaseUrlWhenNoPortalPropertyIsConfigured() {
        PortalProperties emptyProperties = new PortalProperties();
        SupportProperties supportProperties = new SupportProperties();
        supportProperties.getServer().setPortalPublicBaseUrl("https://support.test/portal");
        @SuppressWarnings("unchecked")
        ObjectProvider<SupportProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(supportProperties);
        PortalLoginConfigService supportService = new PortalLoginConfigService(settingRepository,
                identityProviderRepository, new PortalUrlResolver(emptyProperties, provider),
                emptyProperties);

        PortalLoginConfigResponse response = supportService.getLoginConfig("http://localhost:8081");

        assertThat(response.getHomeUrl()).isEqualTo("https://support.test/portal");
        assertThat(response.getLoginUrl()).isEqualTo("https://support.test/portal/login");
    }

    @Test
    void returnsGlobalLogoutUrl() {
        PortalLogoutUrlResponse response = service.getLogoutUrl(null);

        assertThat(response.getLogoutUrl()).isEqualTo("https://home.test/logout");
        assertThat(response.getLoginUrl()).isEqualTo("https://home.test/login");
        assertThat(response.getHomeUrl()).isEqualTo("https://home.test");
        assertThat(response.getMethod()).isEqualTo(PortalConstants.LOGOUT_METHOD_GLOBAL);
    }

    private static PortalLoginMethodView method(PortalLoginConfigResponse response, String code) {
        return response.getMethods().stream()
                .filter(method -> code.equals(method.getCode()))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SupportProperties> supportProvider() {
        ObjectProvider<SupportProperties> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return provider;
    }
}