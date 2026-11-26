package cn.tolim.pudans.core.ai.endpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.contract.ai.dto.AiFile;
import cn.tolim.pudans.contract.ai.dto.AiSpeechRequest;
import cn.tolim.pudans.core.ai.gateway.AiChannels;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.deepseek.DeepSeekGateway;
import cn.tolim.pudans.core.ai.provider.deepseek.DeepSeekProvider;
import cn.tolim.pudans.core.ai.provider.minimax.MiniMaxGateway;
import cn.tolim.pudans.core.ai.provider.AiSpeech;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import cn.tolim.pudans.infra.utils.ReactiveRequestIdentity;
import cn.tolim.pudans.infra.utils.RequestIdentity;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.http.codec.multipart.DefaultPartHttpMessageReader;
import org.springframework.http.codec.multipart.MultipartHttpMessageReader;
import org.springframework.test.web.reactive.server.MockServerConfigurer;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class AiChannelCapabilityEndpointTest {

    private static final String WORKSPACE = "workspace-a";

    private static final Map<String, String> CREDENTIALS = Map.of("apiKey", "secret");

    @Mock
    AiChannels channels;

    @Mock
    DeepSeekGateway gateway;

    @Mock
    MiniMaxGateway miniMaxGateway;

    @Mock
    AiProviderHttp http;

    @Mock
    ReactiveRequestIdentity identity;

    @TempDir
    Path workDir;

    private UUID channelId;

    private WebTestClient client;

    @BeforeEach
    void setUp() {
        var properties = new PudansProperties();
        properties.setWorkDir(workDir);
        channelId = UUID.randomUUID();
        var provider = new DeepSeekProvider(http);
        lenient().when(gateway.provider()).thenReturn("deepseek");
        lenient().when(miniMaxGateway.provider()).thenReturn("minimax");
        var endpoint = new AiChannelCapabilityEndpoint(
                new AiChannelResolver(channels, identity),
                gateway,
                provider,
                List.of(gateway, miniMaxGateway),
                properties);
        var codecs = ServerCodecConfigurer.create();
        var partReader = new DefaultPartHttpMessageReader();
        partReader.setMaxInMemorySize(256 * 1024);
        partReader.setMaxDiskUsagePerPart(100L * 1024 * 1024);
        codecs.defaultCodecs().multipartReader(new MultipartHttpMessageReader(partReader));
        client = WebTestClient.bindToRouterFunction(endpoint.endpoint())
                .apply(new MockServerConfigurer() {
                    @Override
                    public void beforeServerCreated(
                            org.springframework.web.server.adapter.WebHttpHandlerBuilder builder) {
                        builder.codecConfigurer(codecs);
                    }
                })
                .build();
    }

    @Test
    void streamsSynthesizedAudioFromTheDeepSeekTtsApi() {
        resolved("deepseek");
        when(gateway.synthesize(any(), anyMap(), any(AiSpeechRequest.class)))
                .thenReturn(Mono.just(new AiSpeech(
                        "audio/pcm",
                        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(
                                "wave".getBytes(StandardCharsets.UTF_8))))));

        client.post()
                .uri("/aichannels/{name}/-/tts", channelId)
                .bodyValue(new AiSpeechRequest("Hello", "tide", "pcm", false))
                .exchange()
                .expectStatus()
                .isOk()
                .expectHeader()
                .contentType(MediaType.parseMediaType("audio/pcm"))
                .expectBody(String.class)
                .isEqualTo("wave");
    }

    @Test
    void dispatchesSharedCapabilitiesToTheProviderGateway() {
        resolved("minimax");
        when(miniMaxGateway.synthesize(any(), anyMap(), any(AiSpeechRequest.class)))
                .thenReturn(Mono.just(new AiSpeech(
                        "audio/pcm",
                        Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(
                                "wave".getBytes(StandardCharsets.UTF_8))))));

        client.post()
                .uri("/aichannels/{name}/-/tts", channelId)
                .bodyValue(new AiSpeechRequest("Hello", "tide", "pcm", false))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .isEqualTo("wave");

        verify(gateway, never()).synthesize(any(), anyMap(), any(AiSpeechRequest.class));
    }

    @Test
    void forwardsTheAnthropicCompatibilityAlias() {
        resolved("deepseek");
        when(gateway.messages(any(), anyMap(), anyMap(), isNull(), eq(false)))
                .thenReturn(Mono.just("{\"type\":\"message\"}"));

        client.post()
                .uri("/aichannels/{name}/-/anthropic/messages", channelId)
                .bodyValue(Map.of("model", "deepseek-flash"))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody(String.class)
                .isEqualTo("{\"type\":\"message\"}");
    }

    @Test
    void uploadsAFileThroughTheDocumentedMultipartRouteAndRemovesTheSpoolFile() {
        resolved("deepseek");
        when(gateway.uploadFile(
                        any(AiChannelEntity.class),
                        anyMap(),
                        eq("user_data"),
                        eq("note.txt"),
                        eq("text/plain"),
                        any(Path.class),
                        isNull()))
                .thenReturn(Mono.just(new AiFile(
                        "file-api-test", "file", 4L, 1700000000L, "note.txt", "user_data", null)));

        var multipart = new MultipartBodyBuilder();
        multipart.part("purpose", "user_data");
        multipart.part("file", new ByteArrayResource("test".getBytes(StandardCharsets.UTF_8)))
                .filename("note.txt")
                .contentType(MediaType.TEXT_PLAIN);

        client.post()
                .uri("/aichannels/{name}/-/files", channelId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.id")
                .isEqualTo("file-api-test");

        assertThat(workDir.resolve("ai/.tmp")).isEmptyDirectory();
    }

    @Test
    void rejectsTranscriptionFilesAboveTheDocumentedLimit() {
        resolved("deepseek");
        var multipart = new MultipartBodyBuilder();
        multipart.part("file", new ByteArrayResource(new byte[8 * 1024 * 1024 + 1]))
                .filename("speech.wav")
                .contentType(MediaType.parseMediaType("audio/wav"));

        client.post()
                .uri("/aichannels/{name}/-/asr", channelId)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(multipart.build()))
                .exchange()
                .expectStatus()
                .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);

        verifyNoInteractions(gateway);
    }

    @Test
    void rejectsChannelsOwnedByAnotherProvider() {
        resolved("openai");

        client.get()
                .uri("/aichannels/{name}/-/balance", channelId)
                .exchange()
                .expectStatus()
                .isBadRequest();

        verifyNoInteractions(gateway);
    }

    private void resolved(String provider) {
        when(identity.resolve(any()))
                .thenReturn(Mono.just(new RequestIdentity("alice", WORKSPACE)));
        when(channels.resolve(WORKSPACE, channelId))
                .thenReturn(Mono.just(new AiChannels.ResolvedChannel(channel(provider), CREDENTIALS)));
    }

    private AiChannelEntity channel(String provider) {
        var channel = new AiChannelEntity();
        channel.setId(channelId);
        channel.setWorkspaceId(WORKSPACE);
        channel.setProvider(provider);
        channel.setProtocol("chat-completions");
        channel.setBaseUrl("https://api.deepseek.com");
        channel.setTimeoutMs(30_000);
        return channel;
    }
}
