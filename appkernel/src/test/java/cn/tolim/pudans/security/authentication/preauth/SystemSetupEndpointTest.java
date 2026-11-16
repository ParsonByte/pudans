package cn.tolim.pudans.security.authentication.preauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.infra.setting.InitializationStateGetter;
import cn.tolim.pudans.infra.setting.ParameterSettingFetcher;
import cn.tolim.pudans.security.authentication.SuperAdminInitializer;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
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
    cn.tolim.pudans.contract.infra.ExternalUrlSupplier externalUrlSupplier;

    @Mock
    ApplicationEventPublisher eventPublisher;

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
                externalUrlSupplier,
                eventPublisher);
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
}
