package cn.tolim.pudans.security.authentication.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.user.Accounts;
import cn.tolim.pudans.extension.contract.ApiGroups;
import cn.tolim.pudans.core.rbac.permission.UiPermissions;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import cn.tolim.pudans.infra.properties.OidcProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.InitializationStateGetter;
import cn.tolim.pudans.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.infra.setting.ParameterSettingSecretCipher;
import cn.tolim.pudans.infra.setting.SecurityParameters;
import cn.tolim.pudans.security.authentication.AuthProviders;
import cn.tolim.pudans.security.captcha.Captchas;
import cn.tolim.pudans.security.contract.AuthProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class LoginPublicEndpointTest {

    @Mock
    Accounts accounts;

    @Mock
    AuthProviders authProviders;

    @Mock
    InitializationStateGetter initializationStateGetter;

    @Mock
    UiPermissions uiPermissions;

    @Mock
    Captchas captchas;

    @Mock
    ParameterSettingFetcher parameterSettingFetcher;

    @Mock
    ParameterSettingSecretCipher parameterSettingSecretCipher;

    private FeishuProperties feishuProperties;

    private SecurityParameters securitySettings;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        feishuProperties = new FeishuProperties();
        securitySettings = new SecurityParameters(
                parameterSettingFetcher,
                parameterSettingSecretCipher,
                new OidcProperties(),
                feishuProperties);

        var endpoint = new LoginPublicEndpoint(
                new SecurityProperties(),
                accounts,
                authProviders,
                securitySettings,
                captchas,
                initializationStateGetter,
                uiPermissions);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void omitsFeishuWhenCredentialsAreMissing() {
        when(authProviders.getEnabledProviders()).thenReturn(Flux.empty());

        webTestClient
                .get()
                .uri("/login/providers")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(LoginPublicEndpoint.LoginProviderView[].class)
                .value(providers -> assertThat(providers).isEmpty());
    }

    @Test
    void omitsAProviderWithoutConfiguredAuthentication() {
        var provider = new AuthProvider();
        var metadata = new Metadata();
        metadata.setName("missing");
        provider.setMetadata(metadata);
        var spec = new AuthProvider.AuthProviderSpec();
        spec.setDisplayName("Missing");
        spec.setAuthenticationUrl("/oauth2/authorization/missing");
        spec.setAuthType(AuthProvider.AuthType.OAUTH2);
        provider.setSpec(spec);
        when(authProviders.getEnabledProviders()).thenReturn(Flux.just(provider));

        webTestClient
                .get()
                .uri("/login/providers")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(LoginPublicEndpoint.LoginProviderView[].class)
                .value(providers -> assertThat(providers).isEmpty());
    }

    @Test
    void includesFeishuAuthorizationUrlWhenConfigured() {
        when(authProviders.getEnabledProviders()).thenReturn(Flux.empty());
        feishuProperties.setEnabled(true);
        feishuProperties.setAppId("app-id");
        feishuProperties.setAppSecret("app-secret");

        webTestClient
                .get()
                .uri("/login/providers")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(LoginPublicEndpoint.LoginProviderView[].class)
                .value(providers -> {
                    assertThat(providers).hasSize(1);
                    assertThat(providers[0].name()).isEqualTo("feishu");
                    assertThat(providers[0].authenticationUrl())
                            .isEqualTo(ApiGroups.API_PREFIX + "/oauth2/authorization/feishu");
                });
    }
}
