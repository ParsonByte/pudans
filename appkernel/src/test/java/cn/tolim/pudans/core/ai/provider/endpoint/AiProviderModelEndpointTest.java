package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.ProviderAccounts;
import cn.tolim.pudans.core.ai.provider.GenericModelsClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiProviderModelEndpointTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderAccounts accountService;

    @Mock
    DeepSeekModelsClient modelsClient;

    @Mock
    GenericModelsClient genericModelsClient;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var syncService = new cn.tolim.pudans.core.ai.provider.ProviderModelSync(
                client, accountService, modelsClient, genericModelsClient);
        var endpoint = new AiProviderModelEndpoint(syncService);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsModels() {
        var provider = provider();
        var account = account();
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider));
        when(accountService.firstEnabled("provider-a")).thenReturn(Mono.just(account));
        when(modelsClient.list(eq(provider), eq(account)))
                .thenReturn(Mono.just(new DeepSeekModelsClient.ModelList(
                        "list",
                        List.of(new DeepSeekModelsClient.DeepSeekModel(
                                "deepseek-flash",
                                "model",
                                "deepseek",
                                "DeepSeek-V4.1-Flash",
                                1048576,
                                393216,
                                List.of("text", "image"),
                                List.of("text"),
                                new DeepSeekModelsClient.Effort(List.of("low", "high"), "high"),
                                null)))));

        webClient.get()
                .uri("/aiproviders/provider-a/models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.object").isEqualTo("list")
                .jsonPath("$.data[0].id").isEqualTo("deepseek-flash");
    }

    @Test
    void syncsRemoteModelsWithoutDeletingLocalModels() {
        var provider = provider();
        var account = account();
        var local = model("deepseek-flash");
        local.getSpec().setDisplayName("旧名称");
        local.getSpec().setContextWindow(1000);
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider));
        when(accountService.firstEnabled("provider-a")).thenReturn(Mono.just(account));
        when(modelsClient.list(eq(provider), eq(account))).thenReturn(Mono.just(modelList()));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(local));
        when(client.update(any(AiModel.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webClient.post()
                .uri("/aiproviders/provider-a/actions/sync-models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.added").isEqualTo(0)
                .jsonPath("$.updated").isEqualTo(1)
                .jsonPath("$.unchanged").isEqualTo(0)
                .jsonPath("$.removed.length()").isEqualTo(0)
                .jsonPath("$.models[0].modelCode").isEqualTo("deepseek-flash");
    }

    @Test
    void reportsAddedAndRemovedModelsWithoutDeletingThem() {
        var provider = provider();
        var account = account();
        var local = model("legacy-model");
        when(client.get(AiProvider.class, "provider-a")).thenReturn(Mono.just(provider));
        when(accountService.firstEnabled("provider-a")).thenReturn(Mono.just(account));
        when(modelsClient.list(eq(provider), eq(account))).thenReturn(Mono.just(modelList()));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(local));
        when(client.create(any(AiModel.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        webClient.post()
                .uri("/aiproviders/provider-a/actions/sync-models")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.added").isEqualTo(1)
                .jsonPath("$.updated").isEqualTo(0)
                .jsonPath("$.removed[0]").isEqualTo("legacy-model")
                .jsonPath("$.models[0].modelCode").isEqualTo("deepseek-flash");
    }

    private static DeepSeekModelsClient.ModelList modelList() {
        return new DeepSeekModelsClient.ModelList(
                "list",
                List.of(new DeepSeekModelsClient.DeepSeekModel(
                        "deepseek-flash",
                        "model",
                        "deepseek",
                        "DeepSeek-V4.1-Flash",
                        1048576,
                        393216,
                        List.of("text", "image"),
                        List.of("text"),
                        new DeepSeekModelsClient.Effort(List.of("low", "high"), "high"),
                        null)));
    }

    private static AiModel model(String modelCode) {
        var model = new AiModel();
        model.setMetadata(metadata("local-" + modelCode));
        model.getSpec().setProvider("provider-a");
        model.getSpec().setModelCode(modelCode);
        return model;
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

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }
}
