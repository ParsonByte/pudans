package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiModel;
import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.embedding.AiEmbeddingService;
import cn.tolim.pudans.core.ai.embedding.EmbeddingBatch;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.provider.AiProviderAdapter;
import cn.tolim.pudans.core.ai.provider.AiProviderAdapterRegistry;
import cn.tolim.pudans.core.ai.provider.AiProviderAccountService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiProviderEndpointTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    AiProviderAdapter adapter;

    @Mock
    AiEmbeddingService embeddingService;

    @Mock
    AiModelAdapterRegistry modelAdapters;

    @Mock
    AiProviderAccountService accountService;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        when(adapter.protocol()).thenReturn(AiProvider.Protocol.OPENAI_CHAT);
        var registry = new AiProviderAdapterRegistry(List.of(adapter));
        var endpoint =
                new AiProviderEndpoint(client, registry, modelAdapters, embeddingService, accountService);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void testsAProviderThroughItsFirstAccountAndModel() {
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider()));
        when(accountService.firstEnabled("provider-a")).thenReturn(Mono.just(account()));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(model()));
        when(adapter.chat(any(), any(), any(), any())).thenReturn(Mono.just(new AiChatResponse(
                "resp-1", "gpt-test", new AiChatMessage("assistant", List.of()), null, "stop")));

        webClient.post()
                .uri("/aiproviders/provider-a/test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.successful").isEqualTo(true)
                .jsonPath("$.model").isEqualTo("gpt-test");
    }

    @Test
    void updatesProviderAccountQuota() {
        when(client.get(AiProviderAccount.class, "account-a")).thenReturn(Mono.just(account()));
        when(client.update(any(AiProviderAccount.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webClient.post()
                .uri("/aiprovideraccounts/account-a/quota")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"quota":{"unit":"TOKEN","limit":1000,"used":10},
                         "windows":[],"balance":{"unit":"USD","left":5.00}}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.spec.quota.limit").isEqualTo(1000)
                .jsonPath("$.spec.balance.left").isEqualTo(5.0);
    }

    @Test
    void testsAnEmbeddingModel() {
        var model = model();
        model.getSpec().setModelType(AiModel.ModelType.EMBEDDING);
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model));
        when(embeddingService.embed(List.of("Reply with OK."), "model-a"))
                .thenReturn(Mono.just(new EmbeddingBatch("embed-test", 2, List.of(new float[] {0.1f, 0.2f}))));

        webClient.post()
                .uri("/aimodels/model-a/test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.successful").isEqualTo(true)
                .jsonPath("$.model").isEqualTo("embed-test");
    }

    @Test
    void rejectsTestingANonChatModel() {
        var model = model();
        model.getSpec().setModelType(AiModel.ModelType.RERANK);
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model));

        webClient.post()
                .uri("/aimodels/model-a/test")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.BAD_GATEWAY);
    }

    private static AiProvider provider() {
        var provider = new AiProvider();
        provider.setMetadata(metadata("provider-a"));
        provider.getSpec().setDisplayName("供应商 A");
        provider.getSpec().setProtocol(AiProvider.Protocol.OPENAI_CHAT);
        return provider;
    }

    private static AiProviderAccount account() {
        var account = new AiProviderAccount();
        account.setMetadata(metadata("account-a"));
        account.getSpec().setProvider("provider-a");
        account.getSpec().setDisplayName("账号 A");
        return account;
    }

    private static AiModel model() {
        var model = new AiModel();
        model.setMetadata(metadata("model-a"));
        model.getSpec().setProvider("provider-a");
        model.getSpec().setModelCode("gpt-test");
        model.getSpec().setDisplayName("模型 A");
        return model;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
