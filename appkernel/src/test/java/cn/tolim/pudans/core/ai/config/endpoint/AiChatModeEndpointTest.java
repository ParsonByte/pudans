package cn.tolim.pudans.core.ai.config.endpoint;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiChatMode;
import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.config.ChatModes;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class AiChatModeEndpointTest {

    @Mock
    ChatModes chatModeService;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        client = WebTestClient.bindToRouterFunction(new AiChatModeEndpoint(chatModeService).endpoint())
                .build();
    }

    @Test
    void listsEnabledModesWithMetadata() {
        when(chatModeService.available()).thenReturn(Flux.just(
                mode("fast", "快速模式", "fast-group", AiModel.Tier.CHEAP, List.of("kb-a")),
                mode("pro", "专业模式", "pro-group", AiModel.Tier.HIGH, List.of())));

        client.get()
                .uri("/chats/-/modes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].name").isEqualTo("fast")
                .jsonPath("$[0].displayName").isEqualTo("快速模式")
                .jsonPath("$[0].routeGroup").isEqualTo("fast-group")
                .jsonPath("$[0].defaultTier").isEqualTo("CHEAP")
                .jsonPath("$[0].kbCodes[0]").isEqualTo("kb-a")
                .jsonPath("$[1].name").isEqualTo("pro");
    }

    @Test
    void returnsAnEmptyListWhenNoModeIsEnabled() {
        when(chatModeService.available()).thenReturn(Flux.empty());

        client.get()
                .uri("/chats/-/modes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .json("[]");
    }

    @Test
    void toleratesAModeWithoutKnowledgeBases() {
        when(chatModeService.available()).thenReturn(
                Flux.just(mode("plain", "普通模式", "plain-group", null, null)));

        client.get()
                .uri("/chats/-/modes")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.length()").isEqualTo(1)
                .jsonPath("$[0].kbCodes.length()").isEqualTo(0);
    }

    private static AiChatMode mode(
            String name,
            String displayName,
            String routeGroup,
            AiModel.Tier tier,
            List<String> kbCodes) {
        var mode = new AiChatMode();
        var metadata = new Metadata();
        metadata.setName(name);
        mode.setMetadata(metadata);
        mode.getSpec().setDisplayName(displayName);
        mode.getSpec().setRouteGroup(routeGroup);
        mode.getSpec().setDefaultTier(tier);
        mode.getSpec().setKnowledgeBaseCodes(kbCodes);
        return mode;
    }
}
