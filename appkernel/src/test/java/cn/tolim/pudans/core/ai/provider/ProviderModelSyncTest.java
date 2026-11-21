package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.resource.AiModel;
import cn.tolim.pudans.contract.ai.resource.AiProvider;
import cn.tolim.pudans.contract.ai.resource.AiProviderAccount;
import cn.tolim.pudans.contract.extension.Metadata;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient.DeepSeekModel;
import cn.tolim.pudans.core.ai.provider.supplier.deepseek.DeepSeekModelsClient.ModelList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class ProviderModelSyncTest {

    @Mock
    ReactiveExtensionClient client;

    @Mock
    ProviderAccounts providerAccounts;

    @Mock
    DeepSeekModelsClient deepSeekModelsClient;

    @Mock
    GenericModelsClient genericModelsClient;

    @Test
    void createsMissingModelsAndUpdatesChangedOnes() {
        var provider = provider("deepseek", AiProvider.Protocol.DEEPSEEK);
        var account = new AiProviderAccount();
        var existing = model("deepseek-deepseek-chat", "deepseek-chat", "旧名称");
        when(client.get(AiProvider.class, "deepseek")).thenReturn(Mono.just(provider));
        when(providerAccounts.firstEnabled("deepseek")).thenReturn(Mono.just(account));
        when(deepSeekModelsClient.list(provider, account)).thenReturn(Mono.just(new ModelList(
                "list",
                List.of(
                        new DeepSeekModel("deepseek-chat", "model", null, "新名称", 128000, 8192, List.of(), List.of(), null, null),
                        new DeepSeekModel("deepseek-reasoner", "model", null, "推理", 128000, 8192, List.of(), List.of(), null, null)))));
        when(client.listAll(eq(AiModel.class), any(), any())).thenReturn(Flux.just(existing));
        when(client.update(existing)).thenReturn(Mono.just(existing));
        when(client.create(any(AiModel.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        var service = new ProviderModelSync(
                client, providerAccounts, deepSeekModelsClient, genericModelsClient);

        StepVerifier.create(service.sync("deepseek"))
                .assertNext(result -> {
                    assertThat(result.added()).isEqualTo(1);
                    assertThat(result.updated()).isEqualTo(1);
                    assertThat(result.unchanged()).isZero();
                    assertThat(result.removed()).isEmpty();
                    assertThat(result.models()).hasSize(2);
                })
                .verifyComplete();

        verify(client).update(existing);
        assertThat(existing.getSpec().getDisplayName()).isEqualTo("新名称");
    }

    @Test
    void reportsUnsupportedProtocolWithoutCallingUpstream() {
        var provider = provider("custom", AiProvider.Protocol.OLLAMA);
        var account = new AiProviderAccount();
        when(client.get(AiProvider.class, "custom")).thenReturn(Mono.just(provider));
        when(providerAccounts.firstEnabled("custom")).thenReturn(Mono.just(account));
        var service = new ProviderModelSync(
                client, providerAccounts, deepSeekModelsClient, genericModelsClient);

        StepVerifier.create(service.sync("custom"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .hasMessageContaining("不支持"))
                .verify();

        verify(genericModelsClient, never()).list(provider, account);
    }

    private static AiProvider provider(String name, AiProvider.Protocol protocol) {
        var provider = new AiProvider();
        var metadata = new Metadata();
        metadata.setName(name);
        provider.setMetadata(metadata);
        provider.getSpec().setProtocol(protocol);
        provider.getSpec().setEnabled(true);
        return provider;
    }

    private static AiModel model(String name, String code, String displayName) {
        var model = new AiModel();
        var metadata = new Metadata();
        metadata.setName(name);
        model.setMetadata(metadata);
        model.getSpec().setProvider("deepseek");
        model.getSpec().setModelCode(code);
        model.getSpec().setDisplayName(displayName);
        return model;
    }
}