package cn.tolim.pudans.core.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiRouteGroup;
import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class RouteGroupsTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiSettings settingService;

    private RouteGroups service;

    @BeforeEach
    void setUp() {
        service = new RouteGroups(client, settingService);
    }

    @Test
    void defaultGroupUsesTheSettingName() {
        var spec = new AiSetting.Spec();
        spec.setDefaultRouteGroup("default");
        var group = new AiRouteGroup();
        group.setMetadata(metadata("default"));
        when(settingService.spec()).thenReturn(Mono.just(spec));
        when(client.get(AiRouteGroup.class, "default")).thenReturn(Mono.just(group));

        StepVerifier.create(service.defaultGroup())
                .assertNext(found -> assertThat(found.getMetadata().getName()).isEqualTo("default"))
                .verifyComplete();
    }

    @Test
    void defaultGroupFallsBackToTheDefaultFlag() {
        var spec = new AiSetting.Spec();
        var group = new AiRouteGroup();
        group.setMetadata(metadata("fallback"));
        when(settingService.spec()).thenReturn(Mono.just(spec));
        when(client.listAll(org.mockito.ArgumentMatchers.eq(AiRouteGroup.class),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(Flux.just(group));

        StepVerifier.create(service.defaultGroup())
                .assertNext(found -> assertThat(found.getMetadata().getName()).isEqualTo("fallback"))
                .verifyComplete();
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
