package cn.tolim.pudans.core.ai.provider.endpoint;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.core.extension.ai.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekProviderAccess;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiProviderModelEndpointTest {

    @Mock
    DeepSeekProviderAccess access;

    @Mock
    DeepSeekModelsClient modelsClient;

    private WebTestClient webClient;

    @BeforeEach
    void setUp() {
        var endpoint = new AiProviderModelEndpoint(access, modelsClient);
        webClient = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void listsModels() {
        var provider = provider();
        var account = account();
        when(access.requireProvider("provider-a")).thenReturn(Mono.just(provider));
        when(access.requireAccount(provider)).thenReturn(Mono.just(account));
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
