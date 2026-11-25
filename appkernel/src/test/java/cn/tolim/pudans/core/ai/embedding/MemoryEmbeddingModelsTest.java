package cn.tolim.pudans.core.ai.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiChannel;
import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.core.ai.AiModelDirectory;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.AiProvider;
import cn.tolim.pudans.core.ai.provider.AiProviders;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceEntity;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryVectorSpaceRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import io.agentscope.core.embedding.openai.OpenAITextEmbedding;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MemoryEmbeddingModelsTest {

    @Mock
    AiModelDirectory models;

    @Mock
    AiChannelModelRepository channelModels;

    @Mock
    AiChannels channels;

    @Mock
    AiProviders providers;

    @Mock
    MemoryVectorSpaceRepository spaces;

    private MemoryEmbeddingModels embeddingModels;

    @BeforeEach
    void setUp() {
        embeddingModels = new MemoryEmbeddingModels(models, channelModels, channels, providers, spaces);
    }

    @Test
    void buildsOpenAiEmbeddingForOpenAiCompatibleChannels() {
        var model = model();
        var space = space(model.id(), 1536);
        var channelModel = channelModel(model.id());
        var channel = channel(channelModel.channelId(), "custom");
        stubRoute(model, space, channelModel, channel);
        var provider = mock(AiProvider.class);
        when(provider.supportsOpenAiCompatibleEmbedding()).thenReturn(true);
        when(provider.embeddingBaseUrl("https://example.test/v1"))
                .thenReturn("https://example.test/v1");
        when(providers.find("custom")).thenReturn(Optional.of(provider));

        StepVerifier.create(embeddingModels.defaultSpace("workspace"))
                .assertNext(result -> {
                    assertThat(result.embeddingModel()).isInstanceOf(OpenAITextEmbedding.class);
                    assertThat(result.embeddingModel().getDimensions()).isEqualTo(1536);
                })
                .verifyComplete();
    }

    @Test
    void rejectsChannelsWithoutAnOpenAiCompatibleEmbeddingProtocol() {
        var model = model();
        var space = space(model.id(), 1024);
        var channelModel = channelModel(model.id());
        var channel = channel(channelModel.channelId(), "deepseek");
        channelModelsStub(model, space, channelModel, channel);
        when(channels.credentials(channel.id()))
                .thenReturn(Mono.just(Map.of("apiKey", "secret")));
        when(providers.find("deepseek")).thenReturn(Optional.empty());

        StepVerifier.create(embeddingModels.defaultSpace("workspace"))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("memory-embedding-protocol-unsupported");
                })
                .verify();
    }

    private void stubRoute(
            AiModel model,
            MemoryVectorSpaceEntity space,
            AiChannelModel channelModel,
            AiChannel channel) {
        channelModelsStub(model, space, channelModel, channel);
        when(channels.credentials(channel.id()))
                .thenReturn(Mono.just(Map.of("apiKey", "secret")));
    }

    private void channelModelsStub(
            AiModel model,
            MemoryVectorSpaceEntity space,
            AiChannelModel channelModel,
            AiChannel channel) {
        when(models.resolveDefault("workspace", "embedding")).thenReturn(Mono.just(model));
        when(spaces.findActiveByModelId(model.id())).thenReturn(Flux.just(space));
        when(channelModels.findEnabledRoute(model.id(), "workspace"))
                .thenReturn(Mono.just(channelModel));
        when(channels.get("workspace", channel.id())).thenReturn(Mono.just(channel));
    }

    private static AiModel model() {
        return new AiModel(
                UUID.randomUUID(),
                "embedding",
                "Embedding",
                null,
                Map.of("embedding", Map.of()),
                null,
                null,
                Map.of(),
                "active",
                Instant.now(),
                Instant.now());
    }

    private static AiChannelModel channelModel(UUID modelId) {
        return new AiChannelModel(
                UUID.randomUUID(),
                UUID.randomUUID(),
                modelId,
                "text-embedding-v4",
                1,
                100,
                true,
                Map.of(),
                Map.of(),
                Instant.now(),
                Instant.now());
    }

    private static AiChannel channel(UUID channelId, String provider) {
        return new AiChannel(
                channelId,
                "workspace",
                provider,
                "chat-completions",
                "https://example.test/v1",
                null,
                true,
                Map.of(),
                "active",
                30000,
                1,
                100,
                null,
                Map.of(),
                Instant.now(),
                Instant.now());
    }

    private static MemoryVectorSpaceEntity space(UUID modelId, int dimension) {
        var space = new MemoryVectorSpaceEntity();
        space.setId(UUID.randomUUID());
        space.setEmbeddingModelId(modelId);
        space.setModelKey("embedding_model");
        space.setTableName("memory_vectors_embedding_model");
        space.setStorageType("vector");
        space.setDimension(dimension);
        space.setMetric("cosine");
        space.setHnswIndexName("idx_memory_vectors_embedding_model_hnsw");
        space.setStatus("active");
        return space;
    }
}
