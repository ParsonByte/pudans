package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.embedding.Embeddings;
import cn.tolim.pudans.core.ai.embedding.EmbeddingBatch;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.model.AiModelAdapterRegistry;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.langchain4j.LangChain4jChat;
import cn.tolim.pudans.core.ai.rerank.Reranking;
import cn.tolim.pudans.core.ai.rerank.RerankResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    LangChain4jChat chatService;

    @Mock
    Embeddings embeddingService;

    @Mock
    AiModelAdapterRegistry modelAdapters;

    @Mock
    Reranking rerankService;

    @Mock
    ProviderAccounts accountService;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        lenient().when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider()));
        lenient().when(accountService.firstEnabled("provider-a")).thenReturn(Mono.just(account()));
        lenient()
                .when(client.update(any(AiProvider.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        lenient()
                .when(client.update(any(AiProviderAccount.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var endpoint = new AiProviderEndpoint(
                client, chatService, modelAdapters, embeddingService, rerankService, accountService);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void testsAProviderThroughItsFirstAccountAndModel() {
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider()));
        when(accountService.firstEnabled("provider-a")).thenReturn(Mono.just(account()));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(model()));
        when(chatService.chat(
                        any(), any(), any(), eq(AiProvider.Protocol.OPENAI_CHAT), any()))
                .thenReturn(Mono.just(new AiChatResponse(
                        "resp-1", "gpt-test", new AiChatMessage("assistant", List.of()), null, "stop")));

        webClient.post()
                .uri("/aiproviders/provider-a/test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.successful").isEqualTo(true)
                .jsonPath("$.model").isEqualTo("gpt-test");

        var providerCaptor = ArgumentCaptor.forClass(AiProvider.class);
        verify(client).update(providerCaptor.capture());
        assertThat(providerCaptor.getValue().getStatus().getHealthScore()).isEqualTo(100.0);
        assertThat(providerCaptor.getValue().getStatus().getLastTestedAt()).isNotNull();
        var accountCaptor = ArgumentCaptor.forClass(AiProviderAccount.class);
        verify(client).update(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getStatus().getHealthScore()).isEqualTo(100.0);
        assertThat(accountCaptor.getValue().getStatus().getLastUsedAt()).isNotNull();
    }

    @Test
    void arrangesProvidersByTheGivenOrder() {
        var providerB = provider();
        providerB.getMetadata().setName("provider-b");
        when(client.get(AiProvider.class, "provider-b")).thenReturn(Mono.just(providerB));

        webClient.post()
                .uri("/aiproviders/-/arrange")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"names":["provider-b","provider-a"]}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$[0].metadata.name").isEqualTo("provider-b")
                .jsonPath("$[0].spec.order").isEqualTo(0)
                .jsonPath("$[1].metadata.name").isEqualTo("provider-a")
                .jsonPath("$[1].spec.order").isEqualTo(1);
    }

    @Test
    void duplicatesAProviderWithoutCopyingTheApiKey() {
        var sourceAccount = account();
        sourceAccount.getSpec().setApiKey("encrypted-key");
        sourceAccount.getSpec().setConfigured(true);
        when(client.get(AiProvider.class, "provider-copy")).thenReturn(Mono.empty());
        when(client.listAll(eq(AiProviderAccount.class), any(), any()))
                .thenReturn(Flux.just(sourceAccount));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(model()));
        when(client.create(any(AiProvider.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.create(any(AiProviderAccount.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(client.create(any(AiModel.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webClient.post()
                .uri("/aiproviders/provider-a/actions/duplicate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {"name":"provider-copy","displayName":"复制供应商"}
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.metadata.name").isEqualTo("provider-copy")
                .jsonPath("$.spec.displayName").isEqualTo("复制供应商");

        var accountCaptor = ArgumentCaptor.forClass(AiProviderAccount.class);
        verify(client).create(accountCaptor.capture());
        assertThat(accountCaptor.getValue().getSpec().getProvider()).isEqualTo("provider-copy");
        assertThat(accountCaptor.getValue().getSpec().getApiKey()).isNull();
        assertThat(accountCaptor.getValue().getSpec().isConfigured()).isFalse();
        var modelCaptor = ArgumentCaptor.forClass(AiModel.class);
        verify(client).create(modelCaptor.capture());
        assertThat(modelCaptor.getValue().getSpec().getProvider()).isEqualTo("provider-copy");
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
    void testsARerankModelThroughLangChain4jScoring() {
        var model = model();
        model.getSpec().setModelType(AiModel.ModelType.RERANK);
        when(client.get(AiModel.class, "model-a")).thenReturn(Mono.just(model));
        when(rerankService.rerank(
                        any(AiProvider.class),
                        any(AiProviderAccount.class),
                        any(AiModel.class),
                        eq("Reply with OK."),
                        anyList(),
                        eq(1)))
                .thenReturn(Mono.just(new RerankResult(
                        List.of(new RerankResult.ScoredIndex(0, 0.9)))));

        webClient.post()
                .uri("/aimodels/model-a/test")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.successful").isEqualTo(true)
                .jsonPath("$.model").isEqualTo("gpt-test");

        verify(rerankService).rerank(
                any(AiProvider.class),
                any(AiProviderAccount.class),
                any(AiModel.class),
                eq("Reply with OK."),
                anyList(),
                eq(1));
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
