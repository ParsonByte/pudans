package cn.tolim.pudans.core.ai.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiImage;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationRequest;
import cn.tolim.pudans.contract.ai.dto.AiImageGenerationResult;
import cn.tolim.pudans.contract.ai.dto.AiImageUsage;
import cn.tolim.pudans.contract.ai.dto.AiVideoGenerationTask;
import cn.tolim.pudans.contract.ai.dto.AiVideoTaskContent;
import cn.tolim.pudans.contract.ai.dto.AiVoiceInfo;
import cn.tolim.pudans.contract.ai.dto.AiVoiceListRequest;
import cn.tolim.pudans.contract.ai.dto.AiVoiceListResult;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.minimax.MiniMaxGateway;
import cn.tolim.pudans.core.ai.provider.minimax.MiniMaxProvider;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiMiniMaxCapabilityEndpointTest {

    private static final String WORKSPACE = "workspace-a";

    private static final Map<String, String> CREDENTIALS = Map.of("apiKey", "secret");

    @Mock
    AiChannelResolver resolver;

    @Mock
    MiniMaxGateway gateway;

    @Mock
    AiProviderHttp http;

    private UUID channelId;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        channelId = UUID.randomUUID();
        var provider = new MiniMaxProvider(http);
        var endpoint = new AiMiniMaxCapabilityEndpoint(resolver, gateway, provider);
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint()).build();
    }

    @Test
    void createsMiniMaxImageGenerations() {
        resolved();
        when(gateway.imageGenerations(any(), anyMap(), any(AiImageGenerationRequest.class)))
                .thenReturn(Mono.just(new AiImageGenerationResult(
                        1792308678L,
                        List.of(new AiImage(
                                "https://example.com/output-0.jpg", null, "2720x1536", "a cat", null)),
                        "jpeg",
                        new AiImageUsage(1, 7254, 3174, 4080, Map.of(), Map.of()),
                        "request-1")));
        var request = new AiImageGenerationRequest(
                "MiniMax-H3-Image", "a cat", null, 1, "2720x1536", null, null, "jpeg", "url",
                false, null, null, List.of());

        client.post()
                .uri("/aichannels/{name}/-/minimax/image-generations", channelId)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.request_id")
                .isEqualTo("request-1")
                .jsonPath("$.data[0].url")
                .isEqualTo("https://example.com/output-0.jpg");
    }

    @Test
    void streamsImageGenerationEvents() {
        resolved();
        when(gateway.imageGenerationsStream(any(), anyMap(), any(AiImageGenerationRequest.class)))
                .thenReturn(Flux.just(ServerSentEvent.builder(
                                "{\"type\":\"image_generation.partial_succeeded\",\"image_index\":0}")
                        .event("image_generation.partial_succeeded")
                        .build()));
        var request = new AiImageGenerationRequest(
                "MiniMax-H3-Image", "a cat", null, 1, null, null, null, null, null, true,
                null, null, List.of());

        client.post()
                .uri("/aichannels/{name}/-/minimax/image-generations", channelId)
                .bodyValue(request)
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .expectBody(String.class)
                .value(body -> assertThat(body).contains("partial_succeeded"));
    }

    @Test
    void queriesMiniMaxVideoTasks() {
        resolved();
        when(gateway.videoTask(any(), anyMap(), eq("task-1")))
                .thenReturn(Mono.just(new AiVideoGenerationTask(
                        "task-1",
                        "MiniMax-H3",
                        "succeeded",
                        null,
                        1785125529L,
                        1785125946L,
                        new AiVideoTaskContent(
                                "https://cdn/output.mp4", null),
                        "2K",
                        5,
                        Map.of(),
                        "16:9",
                        "generation",
                        "video")));

        client.get()
                .uri("/aichannels/{name}/-/minimax/video-generations/{taskId}", channelId, "task-1")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo("task-1")
                .jsonPath("$.content.url")
                .isEqualTo("https://cdn/output.mp4");
    }

    @Test
    void listsMiniMaxVoices() {
        resolved();
        when(gateway.voiceList(any(), anyMap(), any(AiVoiceListRequest.class)))
                .thenReturn(Mono.just(new AiVoiceListResult(
                        List.of(new AiVoiceInfo(
                                "Chinese (Mandarin)_Reliable_Executive",
                                "沉稳高管",
                                List.of("可靠"),
                                "1970-01-01")),
                        List.of(),
                        List.of())));

        client.post()
                .uri("/aichannels/{name}/-/minimax/voice-list", channelId)
                .bodyValue(new AiVoiceListRequest("all"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.system_voice[0].voice_id")
                .isEqualTo("Chinese (Mandarin)_Reliable_Executive");
    }

    @Test
    void rejectsChannelsOwnedByAnotherProvider() {
        when(resolver.require(any(), eq("minimax")))
                .thenReturn(Mono.error(new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "ai-channel-provider-unsupported",
                        "该渠道的服务商不是 minimax。")));

        client.post()
                .uri("/aichannels/{name}/-/minimax/voice-list", channelId)
                .bodyValue(new AiVoiceListRequest("all"))
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsEmptyBodiesBeforeCallingTheProvider() {
        resolved();

        client.post()
                .uri("/aichannels/{name}/-/minimax/image-generations", channelId)
                .exchange()
                .expectStatus()
                .isBadRequest();

        verify(gateway, never())
                .imageGenerations(any(), anyMap(), any(AiImageGenerationRequest.class));
    }

    private void resolved() {
        when(resolver.require(any(), eq("minimax")))
                .thenReturn(Mono.just(new AiChannels.ResolvedChannel(channel(), CREDENTIALS)));
    }

    private AiChannelEntity channel() {
        var channel = new AiChannelEntity();
        channel.setId(channelId);
        channel.setWorkspaceId(WORKSPACE);
        channel.setProvider("minimax");
        channel.setProtocol("messages");
        channel.setBaseUrl("https://api.minimax.cn");
        channel.setTimeoutMs(30_000);
        return channel;
    }
}
