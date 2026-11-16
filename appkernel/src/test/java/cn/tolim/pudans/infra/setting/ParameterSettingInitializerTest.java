package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.extension.ConfigMap;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.infra.ParameterSetting;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ParameterSettingInitializerTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ParameterSettingSecretCipher secretCipher;

    @Test
    void migratesLegacyConfigMapWhenTheNewOneDoesNotExist() {
        var defaultConfigMap = configMap("parameter-settings-default", Map.of());
        var legacyConfigMap = configMap("system", Map.of("basic", "{\"title\":\"Pudans\"}"));
        var created = new AtomicReference<ConfigMap>();
        when(client.fetch(ConfigMap.class, "parameter-settings-default")).thenReturn(Mono.just(defaultConfigMap));
        when(client.fetch(ConfigMap.class, "parameter-settings"))
                .thenReturn(Mono.empty(), Mono.defer(() -> Mono.just(created.get())));
        when(client.fetch(ConfigMap.class, "system")).thenReturn(Mono.just(legacyConfigMap));
        when(client.create(any(ConfigMap.class))).thenAnswer(invocation -> {
            var configMap = invocation.getArgument(0, ConfigMap.class);
            created.set(configMap);
            return Mono.just(configMap);
        });

        var initializer = new ParameterSettingInitializer(client, secretCipher);
        StepVerifier.create(initializer.onApplicationEvent(new ExtensionInitializedEvent(this)))
                .verifyComplete();

        assertThat(created.get()).isNotNull();
        assertThat(created.get().getMetadata().getName()).isEqualTo(ParameterSetting.PARAMETER_SETTINGS);
        assertThat(created.get().getMetadata().getAnnotations())
                .containsEntry(ParameterSetting.MIGRATION_ANNOTATION, "system");
        assertThat(created.get().getData()).containsEntry("basic", "{\"title\":\"Pudans\"}");
    }

    @Test
    void doesNotOverwriteAnExistingConfigMap() {
        when(client.fetch(ConfigMap.class, "parameter-settings-default"))
                .thenReturn(Mono.just(configMap("parameter-settings-default", Map.of())));
        when(client.fetch(ConfigMap.class, "parameter-settings"))
                .thenReturn(Mono.just(configMap("parameter-settings", Map.of())));

        var initializer = new ParameterSettingInitializer(client, secretCipher);
        StepVerifier.create(initializer.onApplicationEvent(new ExtensionInitializedEvent(this)))
                .verifyComplete();

        verify(client, never()).create(any(ConfigMap.class));
    }

    private static ConfigMap configMap(String name, Map<String, String> data) {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName(name);
        configMap.setMetadata(metadata);
        configMap.setData(new HashMap<>(data));
        return configMap;
    }
}
