package cn.tolim.pudans.core.ai.provider.endpoint;

import cn.tolim.pudans.core.ai.provider.preset.ProviderPresetCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class AiProviderPresetEndpointTest {

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var endpoint = new AiProviderPresetEndpoint(new ProviderPresetCatalog());
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsSupportedAndUnsupportedPresets() {
        client.get()
                .uri("/aiproviders/-/presets")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.supported[0].id").isNotEmpty()
                .jsonPath("$.supported[?(@.id == 'openai')]").isNotEmpty()
                .jsonPath("$.supported[?(@.id == 'anthropic')]").isNotEmpty()
                .jsonPath("$.supported[?(@.id == 'gemini')]").isNotEmpty()
                .jsonPath("$.supported[?(@.id == 'deepseek')]").isNotEmpty()
                .jsonPath("$.supported[?(@.id == 'minimax')]").isNotEmpty()
                .jsonPath("$.supported[?(@.id == 'qianwen')]").isNotEmpty()
                .jsonPath("$.supported[?(@.id == 'deepseek')].vendor").isNotEmpty()
                .jsonPath("$.supported[?(@.id == 'deepseek')].regions[0].baseUrl")
                .isNotEmpty()
                .jsonPath("$.unsupported").isEmpty();
    }
}