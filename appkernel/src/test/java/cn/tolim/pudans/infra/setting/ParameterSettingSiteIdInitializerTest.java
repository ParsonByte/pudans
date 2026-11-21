package cn.tolim.pudans.infra.setting;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.extension.contract.ConfigMap;
import cn.tolim.pudans.extension.contract.Metadata;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class ParameterSettingSiteIdInitializerTest {

    @Mock
    ParameterSettingFetcher parameterSettingFetcher;

    @Mock
    ReactiveExtensionClient client;

    @Test
    void generatesASiteIdWhenItIsMissing() {
        var configMap = configMap();
        when(parameterSettingFetcher.getConfigMap()).thenReturn(Mono.just(configMap));
        when(client.update(any(ConfigMap.class))).thenReturn(Mono.just(configMap));

        var initializer = new ParameterSettingSiteIdInitializer(parameterSettingFetcher, client);
        StepVerifier.create(initializer.onApplicationEvent(new ExtensionInitializedEvent(this)))
                .verifyComplete();

        var server = JsonMapper.shared().readTree(configMap.getData().get("server"));
        assertThat(server.get("siteId").asString()).isNotBlank();
        verify(client).update(any(ConfigMap.class));
    }

    @Test
    void keepsAnExistingSiteId() {
        var configMap = configMap();
        configMap.getData().put("server", "{\"siteId\":\"site-1\"}");
        when(parameterSettingFetcher.getConfigMap()).thenReturn(Mono.just(configMap));

        var initializer = new ParameterSettingSiteIdInitializer(parameterSettingFetcher, client);
        StepVerifier.create(initializer.onApplicationEvent(new ExtensionInitializedEvent(this)))
                .verifyComplete();

        verify(client, never()).update(any(ConfigMap.class));
    }

    private static ConfigMap configMap() {
        var configMap = new ConfigMap();
        var metadata = new Metadata();
        metadata.setName("parameter-settings");
        configMap.setMetadata(metadata);
        configMap.setData(new HashMap<>());
        return configMap;
    }
}
