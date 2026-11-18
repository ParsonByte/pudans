package cn.tolim.pudans.security.authentication.login;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.user.service.UserService;
import cn.tolim.pudans.contract.extension.ApiGroups;
import cn.tolim.pudans.core.rbac.service.UiPermissionService;
import cn.tolim.pudans.infra.properties.FeishuProperties;
import cn.tolim.pudans.infra.properties.OidcProperties;
import cn.tolim.pudans.infra.properties.SecurityProperties;
import cn.tolim.pudans.infra.setting.InitializationStateGetter;
import cn.tolim.pudans.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.infra.setting.ParameterSettingSecretCipher;
import cn.tolim.pudans.infra.setting.SecurityParameterSettingService;
import cn.tolim.pudans.security.authentication.AuthProviderService;
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
    UserService userService;

    @Mock
    AuthProviderService authProviderService;

    @Mock
    InitializationStateGetter initializationStateGetter;

    @Mock
    UiPermissionService uiPermissionService;

    @Mock
    ParameterSettingFetcher parameterSettingFetcher;

    @Mock
    ParameterSettingSecretCipher parameterSettingSecretCipher;

    private FeishuProperties feishuProperties;

    private SecurityParameterSettingService securitySettings;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        feishuProperties = new FeishuProperties();
        securitySettings = new SecurityParameterSettingService(
                parameterSettingFetcher,
                parameterSettingSecretCipher,
                new OidcProperties(),
                feishuProperties);
        when(authProviderService.getEnabledProviders()).thenReturn(Flux.empty());

        var endpoint = new LoginPublicEndpoint(
                new SecurityProperties(),
                userService,
                authProviderService,
                securitySettings,
                initializationStateGetter,
                uiPermissionService);
        webTestClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void includesFeishuAsUnconfiguredWhenCredentialsAreMissing() {
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
                    assertThat(providers[0].displayName()).isEqualTo("飞书");
                    assertThat(providers[0].authenticationUrl()).isNull();
                });
    }

    @Test
    void includesFeishuAuthorizationUrlWhenConfigured() {
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
