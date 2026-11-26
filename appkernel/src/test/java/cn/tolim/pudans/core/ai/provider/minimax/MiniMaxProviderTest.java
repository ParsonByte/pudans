package cn.tolim.pudans.core.ai.provider.minimax;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.provider.AiProviderAuth;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviderModel;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class MiniMaxProviderTest {

    private static final String CATALOG = """
            {
              "object": "list",
              "data": [
                {
                  "id": "MiniMax-M3.1-Flash",
                  "object": "model",
                  "created": 1789556118,
                  "owned_by": "minimax"
                },
                {
                  "id": "MiniMax-M3",
                  "object": "model",
                  "created": 1780272000,
                  "owned_by": "minimax"
                }
              ]
            }
            """;

    @Mock
    AiProviderHttp http;

    @Test
    void describesTheMiniMaxProviderAndItsCapabilities() {
        var descriptor = provider().descriptor();

        assertThat(descriptor.provider()).isEqualTo("minimax");
        assertThat(descriptor.displayName()).isEqualTo("MiniMax");
        assertThat(descriptor.defaultBaseUrl()).isEqualTo("https://api.minimax.cn");
        assertThat(descriptor.defaultProtocol()).isEqualTo("messages");
        assertThat(descriptor.protocols())
                .containsExactly("messages", "chat-completions", "responses");
        assertThat(descriptor.modelTypes()).containsExactly("chat", "image", "video", "audio");
        assertThat(descriptor.capabilities())
                .extracting(capability -> capability.code() + ":" + capability.status())
                .containsExactly(
                        "chat:available",
                        "reasoning:available",
                        "tools:available",
                        "vision:available",
                        "web-search:available",
                        "files:available",
                        "tts:available",
                        "asr:available",
                        "image:available",
                        "video:available",
                        "audio:available");
        assertThat(descriptor.requiresBaseUrl()).isFalse();
    }

    @Test
    void resolvesDocumentedEndpointsFromEveryBaseUrlForm() {
        var provider = provider();

        assertThat(provider.modelsUrl(null)).isEqualTo("https://api.minimax.cn/v1/models");
        assertThat(provider.anthropicModelsUrl(null))
                .isEqualTo("https://api.minimax.cn/anthropic/v1/models");
        assertThat(provider.modelsUrl("https://api.minimax.cn/"))
                .isEqualTo("https://api.minimax.cn/v1/models");
        assertThat(provider.modelsUrl("https://api.minimax.cn/v1"))
                .isEqualTo("https://api.minimax.cn/v1/models");
        assertThat(provider.modelsUrl("https://api.minimax.cn/anthropic"))
                .isEqualTo("https://api.minimax.cn/v1/models");
        assertThat(provider.modelsUrl("https://api.minimax.cn/anthropic/v1"))
                .isEqualTo("https://api.minimax.cn/v1/models");
        assertThat(provider.anthropicBaseUrl("https://api.minimax.cn"))
                .isEqualTo("https://api.minimax.cn/anthropic/v1");
        assertThat(provider.chatCompletionsBaseUrl("https://api.minimax.cn"))
                .isEqualTo("https://api.minimax.cn/v1");
        assertThat(provider.responsesBaseUrl("https://api.minimax.cn"))
                .isEqualTo("https://api.minimax.cn/v1");
        assertThat(provider.filesUploadUrl(null)).isEqualTo("https://api.minimax.cn/v1/files/upload");
        assertThat(provider.ttsUrl(null)).isEqualTo("https://api.minimax.cn/v1/t2a_v2");
        assertThat(provider.asrUrl(null)).isEqualTo("https://api.minimax.cn/v1/speech_to_text");
        assertThat(provider.speechAsyncUrl(null))
                .isEqualTo("https://api.minimax.cn/v1/t2a_async_v2");
        assertThat(provider.speechTaskUrl(null))
                .isEqualTo("https://api.minimax.cn/v1/query/t2a_async_query_v2");
        assertThat(provider.ttsWebSocketUrl(null, false))
                .isEqualTo("wss://api.minimax.cn/ws/v1/t2a_v2");
        assertThat(provider.ttsWebSocketUrl("http://localhost:8080", true))
                .isEqualTo("ws://localhost:8080/ws/v1/t2a_v2_bidi");
        assertThat(provider.responsesInputTokensUrl(null))
                .isEqualTo("https://api.minimax.cn/v1/responses/input_tokens");
        assertThat(provider.imageGenerationsUrl(null))
                .isEqualTo("https://api.minimax.cn/v1/images/generations");
        assertThat(provider.videoGenerationUrl(null))
                .isEqualTo("https://api.minimax.cn/v2/video_generation");
        assertThat(provider.musicGenerationUrl(null))
                .isEqualTo("https://api.minimax.cn/v1/music_generation");
        assertThat(provider.musicCoverUrl(null))
                .isEqualTo("https://api.minimax.cn/v1/music_cover_preprocess");
    }

    @Test
    void discoversProviderModelsAndDocumentedMediaCatalog() {
        var provider = provider();
        when(http.getJson(any(), any(), any(Duration.class))).thenReturn(Mono.just(CATALOG));
        when(http.getJson(any(), any(), any(), any(Duration.class), any(AiProviderAuth.class)))
                .thenReturn(Mono.just("""
                        {
                          "data": [
                            {"id": "speech-2.8-hd", "display_name": "Speech 2.8 HD"},
                            {"id": "MiniMax-M3", "display_name": "Duplicate"}
                          ]
                        }
                        """));

        StepVerifier.create(provider.discover(channel(), Map.of("apiKey", "secret")))
                .assertNext(models -> {
                    assertThat(models).extracting(AiProviderModel::modelId)
                            .startsWith("MiniMax-M3.1-Flash", "MiniMax-M3")
                            .contains("speech-2.8-hd", "MiniMax-H3", "image-01", "asr-1.0");
                    assertThat(models)
                            .filteredOn(model -> model.modelType().value().equals("video"))
                            .hasSize(7);
                    assertThat(models)
                            .filteredOn(model -> model.modelType().value().equals("image"))
                            .hasSize(4);
                    assertThat(models)
                            .filteredOn(model -> model.modelType().value().equals("audio"))
                            .hasSize(9);
                    var first = models.getFirst();
                    assertThat(first.displayName()).isEqualTo("MiniMax M3.1 Flash");
                    assertThat(first.modelType().value()).isEqualTo("chat");
                    assertThat(first.catalog())
                            .containsEntry("provider", "minimax")
                            .containsEntry("model", "MiniMax-M3.1-Flash")
                            .containsEntry("source", "provider")
                            .containsEntry("created", 1789556118L)
                            .containsEntry("ownedBy", "minimax");
                    var speech = models.stream()
                            .filter(model -> model.modelId().equals("speech-2.8-hd"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(speech.displayName()).isEqualTo("Speech 2.8 HD");
                    assertThat(speech.modelType().value()).isEqualTo("audio");
                    var catalogued = models.stream()
                            .filter(model -> model.modelId().equals("MiniMax-H3"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(catalogued.modelType().value()).isEqualTo("video");
                    assertThat(catalogued.displayName()).isEqualTo("MiniMax H3");
                    assertThat(catalogued.features()).containsExactly("video-generation");
                    assertThat(catalogued.catalog())
                            .containsEntry("source", "provider-documented");
                    var image = models.stream()
                            .filter(model -> model.modelId().equals("image-01"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(image.displayName()).isEqualTo("MiniMax Image 01");
                    var audio = models.stream()
                            .filter(model -> model.modelId().equals("speech-2.6-hd"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(audio.displayName()).isEqualTo("MiniMax Speech 2.6 HD");
                    assertThat(audio.features()).containsExactly("audio-generation");
                    assertThat(audio.catalog())
                            .containsEntry("description", "语音合成 HD 模型，韵律与音质表现出色。");
                })
                .verifyComplete();

        verify(http).getJson(
                eq(URI.create("https://api.minimax.cn/v1/models")),
                eq(Map.of("apiKey", "secret")),
                eq(Duration.ofMillis(30_000)));
        verify(http).getJson(
                eq(URI.create("https://api.minimax.cn/anthropic/v1/models")),
                eq(Map.of("apiKey", "secret")),
                eq(Map.of()),
                eq(Duration.ofMillis(30_000)),
                eq(AiProviderAuth.X_API_KEY));
    }

    @Test
    void restoresCanonicalDisplayNameForLowercaseProviderIds() {
        var provider = provider();
        when(http.getJson(any(), any(), any(Duration.class))).thenReturn(Mono.just("""
                {"data":[{"id":"minimax-m2.1-highspeed","owned_by":"minimax"}]}
                """));
        when(http.getJson(any(), any(), any(), any(Duration.class), any(AiProviderAuth.class)))
                .thenReturn(Mono.just("{\"data\":[]}"));

        StepVerifier.create(provider.discover(channel(), Map.of("apiKey", "secret")))
                .assertNext(models -> {
                    var model = models.stream()
                            .filter(item -> item.modelId().equals("minimax-m2.1-highspeed"))
                            .findFirst()
                            .orElseThrow();
                    assertThat(model.displayName()).isEqualTo("MiniMax M2.1 高速版");
                    assertThat(model.modelType().value()).isEqualTo("chat");
                })
                .verifyComplete();
    }

    @Test
    void infersTypesForMediaModelsReturnedByTheProvider() {
        var provider = provider();
        when(http.getJson(any(), any(), any(Duration.class))).thenReturn(Mono.just("""
                {
                  "data": [
                    {"id": "MiniMax-H3", "owned_by": "minimax"},
                    {"id": "speech-2.8-hd", "owned_by": "minimax"},
                    {"id": "MiniMax-H3-Image", "owned_by": "minimax"}
                  ]
                }
                """));
        when(http.getJson(any(), any(), any(), any(Duration.class), any(AiProviderAuth.class)))
                .thenReturn(Mono.just("{\"data\":[]}"));

        StepVerifier.create(provider.discover(channel(), Map.of("apiKey", "secret")))
                .assertNext(models -> {
                    assertThat(models)
                            .filteredOn(model -> model.source().equals("provider"))
                            .extracting(model -> model.modelId() + ":" + model.modelType().value())
                            .containsExactly(
                                    "MiniMax-H3:video",
                                    "speech-2.8-hd:audio",
                                    "MiniMax-H3-Image:image");
                })
                .verifyComplete();
    }

    @Test
    void rejectsCatalogPayloadsWithoutADataArray() {
        var provider = provider();
        when(http.getJson(any(), any(), any(Duration.class))).thenReturn(Mono.just("{}"));
        when(http.getJson(any(), any(), any(), any(Duration.class), any(AiProviderAuth.class)))
                .thenReturn(Mono.just("{\"data\":[]}"));

        StepVerifier.create(provider.discover(channel(), Map.of("apiKey", "secret")))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(ApiException.class);
                    assertThat(((ApiException) error).getCode())
                            .isEqualTo("ai-provider-discovery-invalid-response");
                })
                .verify();
    }

    @Test
    void exposesProfilesForTheDocumentedProtocols() {
        var provider = provider();

        assertThat(provider.protocolProfile(AiProtocol.CHAT_COMPLETIONS))
                .isInstanceOf(MiniMaxChatCompletionsProfile.class);
        assertThat(provider.protocolProfile(AiProtocol.MESSAGES))
                .isInstanceOf(MiniMaxMessagesProfile.class);
        assertThat(provider.protocolProfile(AiProtocol.RESPONSES))
                .isInstanceOf(MiniMaxResponsesProfile.class);
        assertThat(provider.protocolProfile(AiProtocol.SYSTEM_ONE)).isNull();
    }

    @Test
    void acceptsOnlyMiniMaxModelIdentifiers() {
        var provider = provider();

        assertThat(provider.acceptsModel("MiniMax-M3.1-Flash")).isTrue();
        assertThat(provider.acceptsModel(" minimax-m3 ")).isTrue();
        assertThat(provider.acceptsModel("speech-2.8-hd")).isTrue();
        assertThat(provider.acceptsModel("image-01")).isTrue();
        assertThat(provider.acceptsModel("asr-1.0")).isTrue();
        assertThat(provider.acceptsModel("T2V-01")).isTrue();
        assertThat(provider.acceptsModel("gpt-5")).isFalse();
        assertThat(provider.acceptsModel(null)).isFalse();
    }

    @Test
    void mapsThinkingTogglesAndEffortForEveryProtocol() {
        var chat = new MiniMaxChatCompletionsProfile();

        assertThat(chat.reasoningBodyParams(false, "high")).isEmpty();
        assertThat(chat.reasoningBodyParams(true, null)).isEmpty();
        assertThat(chat.reasoningBodyParams(true, "none"))
                .isEqualTo(Map.of("thinking", Map.of("type", "disabled")));
        assertThat(chat.reasoningBodyParams(true, "minimal"))
                .isEqualTo(Map.of(
                        "thinking", Map.of("type", "adaptive"),
                        "reasoning_effort", "low"));
        assertThat(chat.reasoningBodyParams(true, "xhigh"))
                .isEqualTo(Map.of(
                        "thinking", Map.of("type", "adaptive"),
                        "reasoning_effort", "xhigh"));

        var messages = new MiniMaxMessagesProfile();
        assertThat(messages.reasoningBodyParams(true, "medium"))
                .isEqualTo(Map.of(
                        "thinking", Map.of("type", "adaptive"),
                        "output_config", Map.of("effort", "medium")));

        var responses = new MiniMaxResponsesProfile();
        assertThat(responses.reasoningEffort(true, "none")).isNull();
        assertThat(responses.reasoningEffort(true, "minimal")).isEqualTo("minimal");
        assertThat(responses.reasoningEffort(true, "ultra")).isEqualTo("max");
    }

    private MiniMaxProvider provider() {
        return new MiniMaxProvider(http);
    }

    private static AiChannelEntity channel() {
        var channel = new AiChannelEntity();
        channel.setProvider("minimax");
        channel.setProtocol("messages");
        channel.setBaseUrl("https://api.minimax.cn");
        channel.setTimeoutMs(30_000);
        return channel;
    }
}
