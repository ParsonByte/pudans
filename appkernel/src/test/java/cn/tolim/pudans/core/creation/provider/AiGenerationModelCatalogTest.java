package cn.tolim.pudans.core.creation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.AiChannelModel;
import cn.tolim.pudans.contract.ai.AiModel;
import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelModelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelRepository;
import cn.tolim.pudans.infra.r2dbc.ai.AiModelRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class AiGenerationModelCatalogTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    @Mock
    AiModelRepository models;

    @Mock
    AiChannelModelRepository channelModels;

    @Mock
    AiChannelRepository channels;

    private AiGenerationModelCatalog catalog;

    @BeforeEach
    void setUp() {
        catalog = new AiGenerationModelCatalog(models, channelModels, channels);
    }

    @Test
    void exposesReferenceAndEditForImageModelsWithImageInputs() {
        var model = model(
                "minimax/MiniMax-H3-Image",
                "MiniMax H3 Image",
                Map.of(
                        "image",
                        Map.of(
                                "modalities",
                                Map.of("input", java.util.List.of("text", "image")),
                                "catalog",
                                Map.of("description", "图片生成与编辑，支持参考图。"))));
        stubRoute(model);

        StepVerifier.create(catalog.resolve(model.id()))
                .assertNext(profile -> {
                    assertThat(profile.mediaTypes()).contains(GenerationTask.MediaType.IMAGE);
                    assertThat(profile.operations())
                            .contains(
                                    GenerationTask.Operation.TEXT,
                                    GenerationTask.Operation.REFERENCE,
                                    GenerationTask.Operation.EDIT);
                })
                .verifyComplete();
    }

    @Test
    void exposesFrameForVideoModelsThatSupportFirstAndLastFrames() {
        var model = model(
                "minimax/MiniMax-H3",
                "MiniMax H3",
                Map.of(
                        "video",
                        Map.of(
                                "modalities",
                                Map.of("input", java.util.List.of("text", "image")),
                                "catalog",
                                Map.of("description", "多模态视频生成，支持文生、图生、首尾帧与参考素材。"))));
        stubRoute(model);

        StepVerifier.create(catalog.resolve(model.id()))
                .assertNext(profile -> {
                    assertThat(profile.mediaTypes()).contains(GenerationTask.MediaType.VIDEO);
                    assertThat(profile.operations())
                            .contains(
                                    GenerationTask.Operation.TEXT,
                                    GenerationTask.Operation.REFERENCE,
                                    GenerationTask.Operation.FRAME);
                })
                .verifyComplete();
    }

    @Test
    void keepsTextOnlyVideoModelsWithoutImageInputs() {
        var model = model(
                "minimax/text-video",
                "Text Video",
                Map.of(
                        "video",
                        Map.of(
                                "modalities",
                                Map.of("input", java.util.List.of("text")),
                                "catalog",
                                Map.of("description", "纯文本视频生成。"))));
        stubRoute(model);

        StepVerifier.create(catalog.resolve(model.id()))
                .assertNext(profile -> assertThat(profile.operations())
                        .containsExactly(GenerationTask.Operation.TEXT))
                .verifyComplete();
    }

    private void stubRoute(AiModel model) {
        var channelModel = new AiChannelModel(
                UUID.randomUUID(),
                CHANNEL_ID,
                model.id(),
                "provider-model",
                1,
                100,
                true,
                Map.of(),
                Map.of(),
                Instant.now(),
                Instant.now());
        var channel = new AiChannelEntity();
        channel.setId(CHANNEL_ID);
        channel.setProvider("minimax");
        channel.setStatus("active");
        when(models.findById(model.id())).thenReturn(Mono.just(model));
        when(channelModels.findEnabledRoute(model.id())).thenReturn(Mono.just(channelModel));
        when(channels.findById(CHANNEL_ID)).thenReturn(Mono.just(channel));
    }

    private static AiModel model(String logicalName, String displayName, Map<String, Object> capabilities) {
        return new AiModel(
                UUID.randomUUID(),
                logicalName,
                displayName,
                null,
                capabilities,
                null,
                null,
                Map.of(),
                "active",
                Instant.now(),
                Instant.now());
    }
}
