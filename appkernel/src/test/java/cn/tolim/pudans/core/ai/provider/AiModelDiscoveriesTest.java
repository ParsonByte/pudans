package cn.tolim.pudans.core.ai.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.ai.AiModelType;
import cn.tolim.pudans.contract.ai.dto.AiDiscoveredModel;
import cn.tolim.pudans.contract.ai.dto.AiProviderDescriptor;
import cn.tolim.pudans.core.ai.gateway.AiChannelSecrets;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiModelDiscoveriesTest {

    private static final String WORKSPACE = "workspace-a";

    @Mock
    AiChannelRepository channels;

    @Mock
    AiChannelSecrets secrets;

    @Mock
    AiProviders providers;

    @Mock
    AiModelRepository models;

    @Test
    void discoversOnlyModelsReturnedByTheProvider() {
        var channel = channel("custom");
        var provider = new StubProvider(
                "custom",
                List.of(
                        new AiProviderModel("model-a", "Model A", AiModelType.CHAT, "provider"),
                        new AiProviderModel("model-a", "Duplicate", AiModelType.CHAT, "provider"),
                        new AiProviderModel(
                                "model-b", "Model B", AiModelType.EMBEDDING, "provider")));
        when(channels.findByWorkspaceAndId(WORKSPACE, channel.getId()))
                .thenReturn(Mono.just(channel));
        when(secrets.resolve(channel)).thenReturn(Mono.just(Map.of("apiKey", "secret")));
        when(providers.require("custom")).thenReturn(provider);
        when(models.findAll()).thenReturn(Flux.empty());

        var discoveries = new AiModelDiscoveries(channels, secrets, providers, models);

        StepVerifier.create(discoveries.discover(WORKSPACE, channel.getId()))
                .assertNext(result -> {
                    assertThat(result.provider()).isEqualTo("custom");
                    assertThat(result.catalogProvider()).isEqualTo("custom");
                    assertThat(result.catalogFetchedAt()).isEqualTo(result.discoveredAt());
                    assertThat(result.models())
                            .extracting(AiDiscoveredModel::upstreamModelKey)
                            .containsExactly("model-a", "model-b");
                    var chat = result.models().getFirst();
                    assertThat(chat.displayName()).isEqualTo("Model A");
                    assertThat(chat.description()).isNull();
                    assertThat(chat.modelType()).isEqualTo("chat");
                    assertThat(chat.protocol()).isEqualTo("chat-completions");
                    assertThat(chat.protocols()).containsExactly("chat-completions");
                    assertThat(chat.contextWindow()).isNull();
                    assertThat(chat.maxOutputTokens()).isNull();
                    assertThat(chat.capabilities()).containsExactly("chat");
                    assertThat(chat.features()).isEmpty();
                    assertThat(chat.modalities()).isEmpty();
                    assertThat(chat.parameterSchema()).isEmpty();
                    assertThat(chat.catalog())
                            .containsEntry("provider", "custom")
                            .containsEntry("model", "model-a")
                            .containsEntry("source", "provider");
                    assertThat(chat.source()).isEqualTo("provider");
                    assertThat(chat.catalogStatus()).isNull();
                    assertThat(chat.importedModelId()).isNull();
                    assertThat(result.models().get(1).protocol()).isEqualTo("embeddings");
                })
                .verifyComplete();
    }

    @Test
    void marksAlreadyImportedModels() {
        var channel = channel("custom");
        var imported = new AiModel(
                UUID.randomUUID(),
                "custom/model-a",
                "Model A",
                null,
                Map.of("chat", true),
                1_048_576,
                393_216,
                Map.of(),
                "active",
                Instant.now(),
                Instant.now());
        var provider = new StubProvider(
                "custom",
                List.of(new AiProviderModel("model-a", "Model A", AiModelType.CHAT, "provider")));
        when(channels.findByWorkspaceAndId(WORKSPACE, channel.getId()))
                .thenReturn(Mono.just(channel));
        when(secrets.resolve(channel)).thenReturn(Mono.just(Map.of("apiKey", "secret")));
        when(providers.require("custom")).thenReturn(provider);
        when(models.findAll()).thenReturn(Flux.just(imported));

        var discoveries = new AiModelDiscoveries(channels, secrets, providers, models);

        StepVerifier.create(discoveries.discover(WORKSPACE, channel.getId()))
                .assertNext(result -> {
                    var model = result.models().getFirst();
                    assertThat(model.importedModelId()).isEqualTo(imported.id());
                    assertThat(model.contextWindow()).isNull();
                    assertThat(model.maxOutputTokens()).isNull();
                    assertThat(model.features()).isEmpty();
                    assertThat(model.parameterSchema()).isEmpty();
                })
                .verifyComplete();
    }

    @Test
    void passesProviderReturnedMetadataIntoTheDiscovery() {
        var channel = channel("deepseek");
        var provider = new StubProvider(
                "deepseek",
                List.of(new AiProviderModel(
                        "deepseek-flash",
                        "DeepSeek V4.1 Flash",
                        AiModelType.CHAT,
                        "provider",
                        1_048_576,
                        393_216,
                        List.of("text", "image"),
                        List.of("text"),
                        List.of("reasoning", "vision"),
                        Map.of(
                                "version", "DeepSeek-V4.1-Flash-1017",
                                "releasedAt", "2026-10-17",
                                "pricing", Map.of("unit", "USD/1M tokens"),
                                "retired", true))));
        when(channels.findByWorkspaceAndId(WORKSPACE, channel.getId()))
                .thenReturn(Mono.just(channel));
        when(secrets.resolve(channel)).thenReturn(Mono.just(Map.of("apiKey", "secret")));
        when(providers.require("deepseek")).thenReturn(provider);
        when(models.findAll()).thenReturn(Flux.empty());

        var discoveries = new AiModelDiscoveries(channels, secrets, providers, models);

        StepVerifier.create(discoveries.discover(WORKSPACE, channel.getId()))
                .assertNext(result -> {
                    var model = result.models().getFirst();
                    assertThat(model.contextWindow()).isEqualTo(1_048_576);
                    assertThat(model.maxOutputTokens()).isEqualTo(393_216);
                    assertThat(model.features()).containsExactly("reasoning", "vision");
                    assertThat(model.modalities())
                            .isEqualTo(Map.of(
                                    "input", List.of("text", "image"),
                                    "output", List.of("text")));
                    assertThat(model.catalog())
                            .containsEntry("provider", "deepseek")
                            .containsEntry("model", "deepseek-flash")
                            .containsEntry("source", "provider")
                            .containsEntry("version", "DeepSeek-V4.1-Flash-1017")
                            .containsEntry("releasedAt", "2026-10-17")
                            .containsEntry("retired", true)
                            .containsEntry("pricing", Map.of("unit", "USD/1M tokens"));
                    assertThat(model.catalogStatus()).isEqualTo("retired");
                })
                .verifyComplete();
    }

    private static AiChannelEntity channel(String provider) {
        var channel = new AiChannelEntity();
        channel.setId(UUID.randomUUID());
        channel.setWorkspaceId(WORKSPACE);
        channel.setProvider(provider);
        channel.setProtocol("chat-completions");
        channel.setBaseUrl("https://example.test/v1");
        channel.setTimeoutMs(30_000);
        return channel;
    }

    private static final class StubProvider implements AiProvider {

        private final AiProviderDescriptor descriptor;

        private final List<AiProviderModel> models;

        private StubProvider(String provider, List<AiProviderModel> models) {
            this.descriptor = new AiProviderDescriptor(
                    provider,
                    provider,
                    "",
                    "",
                    "",
                    "https://example.test/v1",
                    "chat-completions",
                    List.of("chat-completions"),
                    List.of("chat", "embedding"),
                    List.of(provider),
                    false,
                    List.of());
            this.models = models;
        }

        @Override
        public AiProviderDescriptor descriptor() {
            return descriptor;
        }

        @Override
        public Mono<List<AiProviderModel>> discover(
                AiChannelEntity channel, Map<String, String> credentials) {
            return Mono.just(models);
        }
    }
}
