package cn.tolim.pudans.security.authentication.preauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.infra.setting.InitializationStateGetter;
import cn.tolim.pudans.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.security.authentication.SuperAdminInitializer;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.Validator;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class SystemSetupEndpointTest {

    @Mock
    InitializationStateGetter initializationStateGetter;

    @Mock
    ParameterSettingFetcher parameterSettingFetcher;

    @Mock
    SuperAdminInitializer superAdminInitializer;

    @Mock
    ReactiveExtensionClient client;

    @Mock
    Validator validator;

    @Mock
    ApplicationEventPublisher eventPublisher;

    @Mock
    PasswordEncoder passwordEncoder;

    @Test
    void setupPasswordUsesTheUnifiedMinimumLength() throws Exception {
        var password = SystemSetupEndpoint.SetupRequest.class.getDeclaredField("password");

        assertThat(password.getAnnotation(jakarta.validation.constraints.Size.class).min())
                .isEqualTo(8);
    }

    @Test
    void initializesTheSystemWithoutPresetInstallations() {
        var parameterSettings = new ConfigMap();
        parameterSettings.setMetadata(new Metadata());
        parameterSettings.getMetadata().setName("parameter-settings");
        parameterSettings.setData(new HashMap<>());
        when(parameterSettingFetcher.getConfigMap()).thenReturn(Mono.just(parameterSettings));
        when(superAdminInitializer.initialize(any())).thenReturn(Mono.empty());
        when(client.update(any(ConfigMap.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.fetch(ConfigMap.class, "system-states")).thenReturn(Mono.empty());
        when(client.create(any(ConfigMap.class))).thenAnswer(invocation -> {
            var configMap = invocation.getArgument(0, ConfigMap.class);
            configMap.setMetadata(new Metadata());
            configMap.getMetadata().setName("system-states");
            configMap.setData(new HashMap<>());
            return Mono.just(configMap);
        });

        var endpoint = new SystemSetupEndpoint(
                initializationStateGetter,
                parameterSettingFetcher,
                superAdminInitializer,
                client,
                validator,
                eventPublisher,
                passwordEncoder);
        var request = new SystemSetupEndpoint.SetupRequest();
        request.setUsername("admin");
        request.setPassword("password");
        request.setSiteTitle("Pudans");
        request.setExternalUrl("http://localhost:8090");

        StepVerifier.create(org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                        endpoint, "doInitialize", request, "admin"))
                .verifyComplete();

        verify(superAdminInitializer).initialize(any());
        verify(client, org.mockito.Mockito.atLeastOnce()).update(any(ConfigMap.class));
        verify(client, org.mockito.Mockito.atLeastOnce()).create(any(ConfigMap.class));
    }

    // 初始化中断后必须使用首次提交的密码继续，不能被他人接管。
    @Test
    void rejectsResumeWithDifferentSetupPassword() {
        var stateConfig = new ConfigMap();
        stateConfig.setMetadata(new Metadata());
        stateConfig.getMetadata().setName("system-states");
        stateConfig.setData(new HashMap<>(java.util.Map.of(
                "states",
                "{\"isSetup\":false,\"setupUsername\":\"admin\",\"setupPasswordHash\":\"stored-hash\"}")));
        when(client.fetch(ConfigMap.class, "system-states")).thenReturn(Mono.just(stateConfig));
        when(passwordEncoder.matches("other-password", "stored-hash")).thenReturn(false);

        var endpoint = new SystemSetupEndpoint(
                initializationStateGetter,
                parameterSettingFetcher,
                superAdminInitializer,
                client,
                validator,
                eventPublisher,
                passwordEncoder);
        var request = new SystemSetupEndpoint.SetupRequest();
        request.setUsername("admin");
        request.setPassword("other-password");

        StepVerifier.create(endpoint.doInitialization(request, "admin"))
                .expectError(org.springframework.security.authentication.BadCredentialsException.class)
                .verify();

        verify(superAdminInitializer, org.mockito.Mockito.never()).initialize(any());
    }
}
