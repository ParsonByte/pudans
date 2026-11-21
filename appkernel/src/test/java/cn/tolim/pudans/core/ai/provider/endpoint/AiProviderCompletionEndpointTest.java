package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.provider.AiCompletionChunk;
import cn.tolim.pudans.core.ai.provider.AiCompletionResult;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekCompletionClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiProviderCompletionEndpointTest {

    @Mock
    DeepSeekProviderAccess access;

    @Mock
    DeepSeekCompletionClient completionClient;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new AiProviderCompletionEndpoint(access, completionClient, JsonUtils.mapper());
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void createsCompletion() {
        var provider = provider();
        var account = account();
        var model = model();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireChatModel("flash", provider)).thenReturn(Mono.just(model));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(completionClient.complete(eq(provider), eq(account), eq(model), any()))
                .thenReturn(Mono.just(new AiCompletionResult(
                        "cmpl-1", "deepseek-flash", "return a + b", "stop",
                        new AiTokenUsage(12, 4, null, null))));

        webClient.post()
                .uri("/aiproviders/provider-a/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", "flash", "prompt", "def add(a, b):\n  return"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("cmpl-1")
                .jsonPath("$.text").isEqualTo("return a + b");
    }

    @Test
    void streamsCompletion() {
        var provider = provider();
        var account = account();
        var model = model();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireChatModel("flash", provider)).thenReturn(Mono.just(model));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
        when(completionClient.stream(eq(provider), eq(account), eq(model), any()))
                .thenReturn(Flux.just(new AiCompletionChunk("return", null, null)));

        webClient.post()
                .uri("/aiproviders/provider-a/completions/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("model", "flash", "prompt", "def add(a, b):\n  return"))
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody()
                .consumeWith(result -> {
                    var body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
                    assertThat(body).contains("\"text\":\"return\"");
                    assertThat(body).contains("data: [DONE]");
                });
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("DeepSeek");
        provider.getSpec().setProtocol(AiProvider.Protocol.DEEPSEEK);
        provider.getSpec().setBaseUrl("https://api.deepseek.com");
        return provider;
    }

    private static AiProviderAccount account() {
        var account = new AiProviderAccount();
        account.setMetadata(metadata("account-a"));
        account.getSpec().setProvider("provider-a");
        return account;
    }

    private static AiModel model() {
        var model = new AiModel();
        model.setMetadata(metadata("flash"));
        model.getSpec().setProvider("provider-a");
        model.getSpec().setModelCode("deepseek-flash");
        return model;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
