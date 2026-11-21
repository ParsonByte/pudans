package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.infra.api.ParameterSetting;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import cn.tolim.pudans.infra.properties.OidcProperties;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SecurityParametersTest {

    @Mock
    ParameterSettingFetcher fetcher;

    @Mock
    ParameterSettingSecretCipher secretCipher;

    private OidcProperties oidcProperties;

    private FeishuProperties feishuProperties;

    private SecurityParameters service;

    @BeforeEach
    void setUp() {
        oidcProperties = new OidcProperties();
        feishuProperties = new FeishuProperties();
        lenient()
                .when(secretCipher.isEncrypted(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).startsWith("enc:"));
        lenient()
                .when(secretCipher.decrypt(anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0, String.class).replaceFirst("^enc:", ""));
        service = new SecurityParameters(
                fetcher, secretCipher, oidcProperties, feishuProperties);
    }

    @Test
    void defaultsToSecureSettingsWhenGroupsAreMissing() {
        when(fetcher.fetch(eq(ParameterSetting.Session.GROUP), eq(ParameterSetting.Session.class)))
                .thenReturn(Mono.empty());
        when(fetcher.fetch(eq(ParameterSetting.Authentication.GROUP), eq(ParameterSetting.Authentication.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.refresh()).verifyComplete();

        assertThat(service.isRsaLoginEncryptionEnabled()).isTrue();
        assertThat(service.isArgon2PasswordStorageEnabled()).isTrue();
        assertThat(service.isTotpEnabled()).isTrue();
        assertThat(service.totpIssuer()).isEqualTo("pudans");
        assertThat(service.isPatEnabled()).isTrue();
        assertThat(service.patMaxLifetimeDays()).isZero();
    }

    @Test
    void readsSessionAndAuthenticationSettings() {
        var session = new ParameterSetting.Session();
        session.setRsaLoginEncryptionEnabled(false);
        session.setArgon2PasswordStorageEnabled(false);
        var authentication = new ParameterSetting.Authentication();
        var totp = new ParameterSetting.Totp();
        totp.setEnabled(false);
        totp.setIssuer("Example");
        authentication.setTotp(totp);
        var pat = new ParameterSetting.Pat();
        pat.setEnabled(false);
        pat.setMaxLifetimeDays(30);
        authentication.setPat(pat);
        when(fetcher.fetch(eq(ParameterSetting.Session.GROUP), eq(ParameterSetting.Session.class)))
                .thenReturn(Mono.just(session));
        when(fetcher.fetch(eq(ParameterSetting.Authentication.GROUP), eq(ParameterSetting.Authentication.class)))
                .thenReturn(Mono.just(authentication));

        StepVerifier.create(service.refresh()).verifyComplete();

        assertThat(service.isRsaLoginEncryptionEnabled()).isFalse();
        assertThat(service.isArgon2PasswordStorageEnabled()).isFalse();
        assertThat(service.isTotpEnabled()).isFalse();
        assertThat(service.totpIssuer()).isEqualTo("Example");
        assertThat(service.isPatEnabled()).isFalse();
        assertThat(service.patMaxLifetimeDays()).isEqualTo(30);
    }

    @Test
    void decryptedProviderSecretsAreAvailableAtRuntime() {
        var authentication = new ParameterSetting.Authentication();
        var oauth2 = new ParameterSetting.OAuth2();
        var oauth2Provider = new ParameterSetting.OAuth2Provider();
        oauth2Provider.setRegistrationId("github");
        oauth2Provider.setClientId("client");
        oauth2Provider.setClientSecret("enc:secret");
        oauth2Provider.setAuthorizationUri("https://github.com/login/oauth/authorize");
        oauth2Provider.setTokenUri("https://github.com/login/oauth/access_token");
        oauth2Provider.setUserInfoUri("https://api.github.com/user");
        oauth2.setProviders(List.of(oauth2Provider));
        authentication.setOauth2(oauth2);
        var oidc = new ParameterSetting.Oidc();
        var oidcProvider = new ParameterSetting.OidcProvider();
        oidcProvider.setRegistrationId("example");
        oidcProvider.setClientId("client");
        oidcProvider.setClientSecret("enc:oidc-secret");
        oidcProvider.setAuthorizationUri("https://idp.example.com/authorize");
        oidcProvider.setTokenUri("https://idp.example.com/token");
        oidcProvider.setUserInfoUri("https://idp.example.com/userinfo");
        oidc.setProviders(List.of(oidcProvider));
        authentication.setOidc(oidc);
        when(fetcher.fetch(eq(ParameterSetting.Session.GROUP), eq(ParameterSetting.Session.class)))
                .thenReturn(Mono.empty());
        when(fetcher.fetch(eq(ParameterSetting.Authentication.GROUP), eq(ParameterSetting.Authentication.class)))
                .thenReturn(Mono.just(authentication));

        StepVerifier.create(service.refresh()).verifyComplete();

        assertThat(service.oauth2Providers()).hasSize(1);
        assertThat(service.oauth2Providers().get(0).getClientSecret()).isEqualTo("secret");
        assertThat(service.oidcProviders()).hasSize(1);
        assertThat(service.oidcProviders().get(0).getClientSecret()).isEqualTo("oidc-secret");
    }

    @Test
    void fallsBackToConfiguredOidcAndFeishuProperties() {
        var oidcProvider = new OidcProperties.Provider();
        oidcProvider.setRegistrationId("property-provider");
        oidcProperties.setProviders(List.of(oidcProvider));
        feishuProperties.setEnabled(true);
        feishuProperties.setAppId("property-app");
        feishuProperties.setAppSecret("property-secret");
        when(fetcher.fetch(eq(ParameterSetting.Session.GROUP), eq(ParameterSetting.Session.class)))
                .thenReturn(Mono.empty());
        when(fetcher.fetch(eq(ParameterSetting.Authentication.GROUP), eq(ParameterSetting.Authentication.class)))
                .thenReturn(Mono.empty());

        StepVerifier.create(service.refresh()).verifyComplete();

        assertThat(service.oidcProviders()).hasSize(1);
        assertThat(service.oidcProviders().get(0).getRegistrationId()).isEqualTo("property-provider");
        assertThat(service.feishu().isConfigured()).isTrue();
        assertThat(service.feishu().appId()).isEqualTo("property-app");
    }
}
