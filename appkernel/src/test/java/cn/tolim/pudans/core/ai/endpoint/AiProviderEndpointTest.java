package cn.tolim.pudans.core.ai.endpoint;

import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiProviderCapability;
import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;

@ExtendWith(MockitoExtension.class)
class AiProviderEndpointTest {

    @Mock
    AiProviders providers;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiProviderEndpoint(providers);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsTheBuiltInProviderCatalog() {
        when(providers.list()).thenReturn(List.of(new AiProviderDescriptor(
                "deepseek",
                "DeepSeek",
                "DeepSeek",
                "https://www.deepseek.com",
                "https://api-docs.deepseek.com",
                "https://api.deepseek.com",
                "openai-compatible",
                List.of("openai-compatible"),
                List.of("chat"),
                List.of("deepseek"),
                false,
                List.of(new AiProviderCapability("chat", "对话", AiProviderCapability.AVAILABLE)))));

        client.get()
                .uri("/aiproviders")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.length()")
                .isEqualTo(1)
                .jsonPath("$[0].provider")
                .isEqualTo("deepseek")
                .jsonPath("$[0].capabilities[0].code")
                .isEqualTo("chat");
    }
}
