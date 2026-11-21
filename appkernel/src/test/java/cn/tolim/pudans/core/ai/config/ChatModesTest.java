package cn.tolim.pudans.core.ai.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiChatMode;
import cn.tolim.pudans.contract.ai.AiRouteGroup;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ChatModesTest {

    @Mock
    ReactiveExtensionClient client;

    @InjectMocks
    ChatModes service;

    @Test
    void resolvesTheRouteGroupOfAMode() {
        var mode = new AiChatMode();
        mode.setMetadata(metadata("fast"));
        mode.getSpec().setDisplayName("快速模式");
        mode.getSpec().setRouteGroup("fast-group");
        var group = new AiRouteGroup();
        group.setMetadata(metadata("fast-group"));
        group.getSpec().setDisplayName("快速路由组");
        when(client.get(AiChatMode.class, "fast")).thenReturn(Mono.just(mode));
        when(client.get(AiRouteGroup.class, "fast-group")).thenReturn(Mono.just(group));

        StepVerifier.create(service.routeGroup("fast"))
                .assertNext(found -> assertThat(found.getMetadata().getName()).isEqualTo("fast-group"))
                .verifyComplete();
    }

    @Test
    void rejectsAModeWithoutRouteGroup() {
        var mode = new AiChatMode();
        mode.setMetadata(metadata("broken"));
        when(client.get(AiChatMode.class, "broken")).thenReturn(Mono.just(mode));

        StepVerifier.create(service.routeGroup("broken"))
                .expectError(IllegalStateException.class)
                .verify();
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
