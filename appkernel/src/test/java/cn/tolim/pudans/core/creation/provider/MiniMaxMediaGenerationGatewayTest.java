package cn.tolim.pudans.core.creation.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiImage;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationTask;
import cn.tolim.pudans.contract.ai.dto.AiVideoTaskContent;
import cn.tolim.pudans.contract.ai.dto.AiVideoTaskSubmitted;
import cn.tolim.pudans.contract.creation.Asset;
import cn.tolim.pudans.contract.creation.GenerationTask;
import cn.tolim.pudans.core.ai.gateway.AiChannelSecrets;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.minimax.MiniMaxGateway;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelRepository;
import cn.tolim.pudans.infra.r2dbc.creation.AssetRepository;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MiniMaxMediaGenerationGatewayTest {

    private static final UUID CHANNEL_ID = UUID.randomUUID();

    private static final UUID MODEL_ID = UUID.randomUUID();

    private static final UUID OWNER_ID = UUID.randomUUID();

    private static final Map<String, String> CREDENTIALS = Map.of("apiKey", "secret");

    @Mock
    MiniMaxGateway gateway;

    @Mock
    AiChannelRepository channels;

    @Mock
    AiChannelSecrets secrets;

    @Mock
    AssetRepository assets;

    @Mock
    StorageTransfer storageTransfer;

    @Mock
    AiProviderHttp http;

    private MiniMaxMediaGenerationGateway mediaGateway;

    private AiChannelEntity channel;

    @BeforeEach
    void setUp() {
        mediaGateway = new MiniMaxMediaGenerationGateway(
                gateway, channels, secrets, assets, storageTransfer, http);
        channel = new AiChannelEntity();
        channel.setId(CHANNEL_ID);
        channel.setProvider("minimax");
        channel.setStatus("active");
        channel.setBaseUrl("https://api.minimax.cn");
    }

    @Test
    void submitsTextToImageAndDownloadsTheProviderResult() {
        var bytes = "image-bytes".getBytes(StandardCharsets.UTF_8);
        when(channels.findById(CHANNEL_ID)).thenReturn(Mono.just(channel));
        when(secrets.resolve(channel)).thenReturn(Mono.just(CREDENTIALS));
        when(gateway.imageGenerations(eq(channel), eq(CREDENTIALS), any()))
                .thenReturn(Mono.just(new AiImageGenerationResult(
                        1L,
                        List.of(new AiImage("https://cdn.example/image.png", null, null, null, null)),
                        "png",
                        null,
                        "request-1")));
        when(http.getBinaryStream(eq(URI.create("https://cdn.example/image.png")), any(Duration.class)))
                .thenReturn(Flux.just(buffer(bytes)));

        var result = mediaGateway.submit(request(
                GenerationTask.MediaType.IMAGE,
                GenerationTask.Operation.TEXT,
                List.of(),
                Map.of("quantity", 2, "ratio", "16:9", "resolution", "1080P")));

        StepVerifier.create(result)
                .assertNext(submission -> {
                    assertThat(submission.channelId()).isEqualTo(CHANNEL_ID);
                    assertThat(submission.state().phase()).isEqualTo(MediaGenerationGateway.Phase.SUCCEEDED);
                    assertThat(submission.state().providerTaskId()).isEqualTo("request-1");
                    assertThat(submission.state().result().assets()).hasSize(1);
                    var asset = submission.state().result().assets().getFirst();
                    assertThat(asset.providerAssetId()).isEqualTo("request-1-0");
                    assertThat(asset.contentType()).isEqualTo("image/png");
                    assertThat(readBytes(asset.body())).isEqualTo(bytes);
                })
                .verifyComplete();

        var body = ArgumentCaptor.forClass(AiImageGenerationRequest.class);
        verify(gateway).imageGenerations(eq(channel), eq(CREDENTIALS), body.capture());
        assertThat(body.getValue().n()).isEqualTo(2);
        assertThat(body.getValue().aspectRatio()).isEqualTo("16:9");
        assertThat(body.getValue().area()).isEqualTo("1K");
        verify(gateway, never()).imageEdits(any(), any(), any());
    }

    @Test
    void submitsImageEditWithTheRealInputAsset() {
        var assetId = UUID.randomUUID();
        var inputBytes = "input-image".getBytes(StandardCharsets.UTF_8);
        var outputBytes = "output-image".getBytes(StandardCharsets.UTF_8);
        when(channels.findById(CHANNEL_ID)).thenReturn(Mono.just(channel));
        when(secrets.resolve(channel)).thenReturn(Mono.just(CREDENTIALS));
        when(assets.findOwned(assetId, new RequestIdentity(OWNER_ID.toString(), "workspace-1")))
                .thenReturn(Mono.just(asset(assetId, "storage-1", "image/jpeg")));
        when(storageTransfer.readBytes("storage-1", 100 * 1024 * 1024))
                .thenReturn(Mono.just(inputBytes));
        when(gateway.imageEdits(eq(channel), eq(CREDENTIALS), any()))
                .thenReturn(Mono.just(new AiImageGenerationResult(
                        1L,
                        List.of(new AiImage(
                                null,
                                Base64.getEncoder().encodeToString(outputBytes),
                                null,
                                null,
                                null)),
                        "png",
                        null,
                        "request-2")));

        StepVerifier.create(mediaGateway.submit(request(
                        GenerationTask.MediaType.IMAGE,
                        GenerationTask.Operation.REFERENCE,
                        List.of(assetId.toString()),
                        Map.of())))
                .assertNext(submission -> {
                    assertThat(submission.state().phase()).isEqualTo(MediaGenerationGateway.Phase.SUCCEEDED);
                    assertThat(readBytes(submission.state().result().assets().getFirst().body()))
                            .isEqualTo(outputBytes);
                })
                .verifyComplete();

        var body = ArgumentCaptor.forClass(AiImageGenerationRequest.class);
        verify(gateway).imageEdits(eq(channel), eq(CREDENTIALS), body.capture());
        assertThat(body.getValue().images()).hasSize(1);
        assertThat(body.getValue().images().getFirst().imageUrl())
                .startsWith("data:image/jpeg;base64,");
        verify(gateway, never()).imageGenerations(any(), any(), any());
    }

    @Test
    void submitsVideoAndMapsTheProviderTaskState() {
        when(channels.findById(CHANNEL_ID)).thenReturn(Mono.just(channel));
        when(secrets.resolve(channel)).thenReturn(Mono.just(CREDENTIALS));
        when(gateway.videoGeneration(eq(channel), eq(CREDENTIALS), any()))
                .thenReturn(Mono.just(new AiVideoTaskSubmitted("task-1")));

        StepVerifier.create(mediaGateway.submit(request(
                        GenerationTask.MediaType.VIDEO,
                        GenerationTask.Operation.TEXT,
                        List.of(),
                        Map.of("resolution", "720P", "duration", 5, "ratio", "16:9"))))
                .assertNext(submission -> {
                    assertThat(submission.providerTaskId()).isEqualTo("task-1");
                    assertThat(submission.state().phase()).isEqualTo(MediaGenerationGateway.Phase.PENDING);
                })
                .verifyComplete();

        var body = ArgumentCaptor.forClass(AiVideoGenerationRequest.class);
        verify(gateway).videoGeneration(eq(channel), eq(CREDENTIALS), body.capture());
        assertThat(body.getValue().resolution()).isEqualTo("768P");
        assertThat(body.getValue().duration()).isEqualTo(5);
        assertThat(body.getValue().ratio()).isEqualTo("16:9");
    }

    @Test
    void findsTheVideoTaskAndDownloadsTheGeneratedFile() {
        var videoBytes = "video-bytes".getBytes(StandardCharsets.UTF_8);
        when(channels.findById(CHANNEL_ID)).thenReturn(Mono.just(channel));
        when(secrets.resolve(channel)).thenReturn(Mono.just(CREDENTIALS));
        when(gateway.videoTask(channel, CREDENTIALS, "task-1"))
                .thenReturn(Mono.just(new AiVideoGenerationTask(
                        "task-1",
                        "MiniMax-H3",
                        "succeeded",
                        null,
                        1L,
                        2L,
                        new AiVideoTaskContent("https://cdn.example/video.mp4", "prompt"),
                        "768P",
                        5,
                        Map.of(),
                        "16:9",
                        "generation",
                        "video")));
        when(http.getBinaryStream(eq(URI.create("https://cdn.example/video.mp4")), any(Duration.class)))
                .thenReturn(Flux.just(buffer(videoBytes)));

        StepVerifier.create(mediaGateway.find(new MediaGenerationGateway.Reference(
                        "client-1",
                        "task-1",
                        null,
                        CHANNEL_ID,
                        MODEL_ID,
                        "MiniMax-H3",
                        GenerationTask.MediaType.VIDEO)))
                .assertNext(state -> {
                    assertThat(state.phase()).isEqualTo(MediaGenerationGateway.Phase.SUCCEEDED);
                    assertThat(state.result().assets()).hasSize(1);
                    assertThat(readBytes(state.result().assets().getFirst().body())).isEqualTo(videoBytes);
                })
                .verifyComplete();
    }

    @Test
    void mapsVideoFailureToAStableError() {
        when(channels.findById(CHANNEL_ID)).thenReturn(Mono.just(channel));
        when(secrets.resolve(channel)).thenReturn(Mono.just(CREDENTIALS));
        when(gateway.videoTask(channel, CREDENTIALS, "task-2"))
                .thenReturn(Mono.just(new AiVideoGenerationTask(
                        "task-2",
                        "MiniMax-H3",
                        "failed",
                        new cn.tolim.pudans.contract.ai.dto.AiVideoError("1001", "生成失败"),
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of(),
                        null,
                        null,
                        null)));

        StepVerifier.create(mediaGateway.find(new MediaGenerationGateway.Reference(
                        "client-2",
                        "task-2",
                        null,
                        CHANNEL_ID,
                        MODEL_ID,
                        "MiniMax-H3",
                        GenerationTask.MediaType.VIDEO)))
                .assertNext(state -> {
                    assertThat(state.phase()).isEqualTo(MediaGenerationGateway.Phase.FAILED);
                    assertThat(state.errorCode()).isEqualTo("1001");
                    assertThat(state.errorMessage()).isEqualTo("生成失败");
                })
                .verifyComplete();
    }

    private static MediaGenerationGateway.Request request(
            GenerationTask.MediaType mediaType,
            GenerationTask.Operation operation,
            List<String> inputAssetIds,
            Map<String, Object> settings) {
        return new MediaGenerationGateway.Request(
                OWNER_ID.toString(),
                "workspace-1",
                "client-1",
                UUID.randomUUID(),
                UUID.randomUUID(),
                CHANNEL_ID,
                MODEL_ID,
                "MiniMax-H3",
                mediaType,
                operation,
                "prompt",
                settings,
                inputAssetIds,
                null);
    }

    private static Asset asset(UUID id, String storageFileId, String contentType) {
        return new Asset(
                id,
                OWNER_ID.toString(),
                "workspace-1",
                "image",
                "creation",
                storageFileId,
                null,
                null,
                "input.jpg",
                null,
                null,
                Map.of(),
                contentType,
                10L,
                null,
                null,
                null,
                List.of(),
                Map.of(),
                "active",
                null,
                null);
    }

    private static DataBuffer buffer(byte[] bytes) {
        return DefaultDataBufferFactory.sharedInstance.wrap(bytes);
    }

    private static byte[] readBytes(Flux<DataBuffer> body) {
        var buffer = DataBufferUtils.join(body).block();
        try {
            var bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}
