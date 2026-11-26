package cn.tolim.pudans.core.ai.provider.deepseek;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.protocol.AiProtocol;
import cn.tolim.pudans.core.ai.provider.AiProviderHttp;
import cn.tolim.pudans.core.ai.provider.AiProviderModel;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.r2dbc.ai.AiChannelEntity;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class DeepSeekProviderTest {

    private static final String CATALOG = """
            {
              "object": "list",
              "data": [
                {
                  "id": "deepseek-flash",
                  "object": "model",
                  "owned_by": "deepseek",
                  "name": "DeepSeek-V4.1-Flash",
                  "context_window": 1048576,
                  "max_output_tokens": 393216,
                  "input_modalities": ["text", "image"],
                  "output_modalities": ["text"],
                  "effort": {
                    "supported_levels": ["low", "high", "max"],
                    "default_level": "high"
                  },
                  "api_capabilities": {
                    "anthropic_messages": {
                      "system_prompt_update": "in-history"
                    }
                  }
                },
                {
                  "id": "deepseek-v4-pro",
                  "object": "model",
                  "owned_by": "deepseek",
                  "name": "DeepSeek-V4-Pro",
                  "context_window": 1048576,
                  "max_output_tokens": 393216,
                  "input_modalities": ["text"],
                  "output_modalities": ["text"],
                  "effort": {
                    "supported_levels": ["low", "high", "max"],
                    "default_level": "high"
                  }
                }
              ]
            }
            """;

    @Mock
    AiProviderHttp http;

    @Test
    void describesTheDeepSeekProviderAndItsCapabilities() {
        var provider = provider();
        var descriptor = provider.descriptor();

        assertThat(descriptor.provider()).isEqualTo("deepseek");
        assertThat(descriptor.displayName()).isEqualTo("DeepSeek");
        assertThat(descriptor.defaultBaseUrl()).isEqualTo("https://api.deepseek.com");
        assertThat(descriptor.defaultProtocol()).isEqualTo("chat-completions");
        assertThat(descriptor.protocols())
                .containsExactly("chat-completions", "messages", "responses");
        assertThat(descriptor.modelTypes()).containsExactly("chat");
        assertThat(descriptor.capabilities())
                .extracting(capability -> capability.code() + ":" + capability.status())
                .containsExactly(
                        "chat:available",
                        "reasoning:available",
                        "tools:available",
                        "vision:available",
                        "web-search:available",
                        "files:available",
                        "ocr:available",
                        "tts:available",
                        "asr:available",
                        "balance:available");
    }

    @Test
    void resolvesTheModelCatalogUrlFromEveryDocumentedBaseUrl() {
        var provider = provider();

        assertThat(provider.modelsUrl(null)).isEqualTo("https://api.deepseek.com/models");
        assertThat(provider.modelsUrl("https://api.deepseek.com/"))
                .isEqualTo("https://api.deepseek.com/models");
        assertThat(provider.modelsUrl("https://api.deepseek.com/anthropic"))
                .isEqualTo("https://api.deepseek.com/models");
        assertThat(provider.modelsUrl("https://api.deepseek.com/anthropic/"))
                .isEqualTo("https://api.deepseek.com/models");
        assertThat(provider.modelsUrl("https://api.deepseek.com/anthropic/v1"))
                .isEqualTo("https://api.deepseek.com/models");
        assertThat(provider.modelsUrl("https://api.deepseek.com/v1"))
                .isEqualTo("https://api.deepseek.com/models");
        assertThat(provider.modelsUrl("https://api.deepseek.com/beta"))
                .isEqualTo("https://api.deepseek.com/models");
    }

    @Test
    void resolvesTheAnthropicVersionedBaseUrl() {
        var provider = provider();

        assertThat(provider.anthropicBaseUrl(null))
                .isEqualTo("https://api.deepseek.com/anthropic/v1");
        assertThat(provider.anthropicBaseUrl("https://api.deepseek.com/anthropic"))
                .isEqualTo("https://api.deepseek.com/anthropic/v1");
        assertThat(provider.anthropicBaseUrl("https://api.deepseek.com/anthropic/v1"))
                .isEqualTo("https://api.deepseek.com/anthropic/v1");
        assertThat(provider.anthropicBaseUrl("https://api.deepseek.com/v1"))
                .isEqualTo("https://api.deepseek.com/anthropic/v1");
    }

    @Test
    void resolvesTheDocumentedApiPathsFromEveryBaseUrl() {
        var provider = provider();

        assertThat(provider.chatCompletionsUrl(channel("https://api.deepseek.com")))
                .isEqualTo("https://api.deepseek.com/chat/completions");
        assertThat(provider.chatCompletionsUrl(channel("https://api.deepseek.com/v1")))
                .isEqualTo("https://api.deepseek.com/chat/completions");
        assertThat(provider.chatCompletionsUrl(channel("https://api.deepseek.com/beta")))
                .isEqualTo("https://api.deepseek.com/beta/chat/completions");
        assertThat(provider.messagesUrl("https://api.deepseek.com/anthropic", true))
                .isEqualTo("https://api.deepseek.com/anthropic/v1/messages");
        assertThat(provider.messagesUrl("https://api.deepseek.com/anthropic/v1", true))
                .isEqualTo("https://api.deepseek.com/anthropic/v1/messages");
        assertThat(provider.messagesUrl("https://api.deepseek.com/anthropic", false))
                .isEqualTo("https://api.deepseek.com/anthropic/messages");
        assertThat(provider.fimUrl("https://api.deepseek.com/beta"))
                .isEqualTo("https://api.deepseek.com/beta/completions");
        assertThat(provider.toolsUrl("https://api.deepseek.com/v1", "search"))
                .isEqualTo("https://api.deepseek.com/v1/tools/search");
        assertThat(provider.ttsWebSocketUrl("https://api.deepseek.com/anthropic"))
                .isEqualTo("wss://api.deepseek.com/v1/tools/tts");
        assertThat(provider.ttsWebSocketUrl("http://localhost:8091"))
                .isEqualTo("ws://localhost:8091/v1/tools/tts");
    }

    @Test
    void parsesTheDocumentedModelCatalogWithLimitsAndModalities() {
        var provider = provider();
        var channel = channel("https://api.deepseek.com");
        when(http.getJson(any(), eq(Map.of("apiKey", "secret")), any(Duration.class)))
                .thenReturn(Mono.just(CATALOG));

        StepVerifier.create(provider.discover(channel, Map.of("apiKey", "secret")))
                .assertNext(models -> {
                    assertThat(models).hasSize(2);

                    var flash = models.getFirst();
                    assertThat(flash.modelId()).isEqualTo("deepseek-flash");
                    assertThat(flash.displayName()).isEqualTo("DeepSeek-V4.1-Flash");
                    assertThat(flash.contextWindow()).isEqualTo(1_048_576);
                    assertThat(flash.maxOutputTokens()).isEqualTo(393_216);
                    assertThat(flash.inputModalities()).containsExactly("text", "image");
                    assertThat(flash.outputModalities()).containsExactly("text");
                    assertThat(flash.features()).containsExactly("reasoning", "vision");
                    assertThat(flash.catalog())
                            .containsEntry("provider", "deepseek")
                            .containsEntry("model", "deepseek-flash")
                            .containsEntry("source", "provider")
                            .containsEntry("ownedBy", "deepseek")
                            .containsEntry(
                                    "description",
                                    "DeepSeek 高性价比对话模型，支持深度思考、工具调用、图像理解与长上下文。");
                    assertThat(flash.catalog().get("effort"))
                            .isEqualTo(Map.of(
                                    "supportedLevels", List.of("low", "high", "max"),
                                    "defaultLevel", "high"));
                    assertThat(flash.catalog().get("apiCapabilities"))
                            .isEqualTo(Map.of(
                                    "anthropic_messages",
                                    Map.of("system_prompt_update", "in-history")));

                    var pro = models.get(1);
                    assertThat(pro.modelId()).isEqualTo("deepseek-v4-pro");
                    assertThat(pro.catalog())
                            .containsEntry(
                                    "description",
                                    "DeepSeek 旗舰推理模型，支持深度思考、工具调用与长上下文。");
                    assertThat(pro.inputModalities()).containsExactly("text");
                    assertThat(pro.features()).containsExactly("reasoning");
                })
                .verifyComplete();

        var uri = ArgumentCaptor.forClass(URI.class);
        verify(http).getJson(uri.capture(), eq(Map.of("apiKey", "secret")), eq(Duration.ofMillis(30_000)));
        assertThat(uri.getValue()).isEqualTo(URI.create("https://api.deepseek.com/models"));
    }

    @Test
    void rejectsCatalogPayloadsWithoutADataArray() {
        var provider = provider();
        when(http.getJson(any(), any(), any(Duration.class))).thenReturn(Mono.just("{}"));

        StepVerifier.create(provider.discover(channel("https://api.deepseek.com"), Map.of("apiKey", "secret")))
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
                .isInstanceOf(DeepSeekChatCompletionsProfile.class);
        assertThat(provider.protocolProfile(AiProtocol.MESSAGES))
                .isInstanceOf(DeepSeekMessagesProfile.class);
        assertThat(provider.protocolProfile(AiProtocol.RESPONSES))
                .isInstanceOf(DeepSeekResponsesProfile.class);
        assertThat(provider.protocolProfile(AiProtocol.SYSTEM_ONE)).isNull();
    }

    @Test
    void acceptsOnlyDeepSeekModelIdentifiers() {
        var provider = provider();

        assertThat(provider.acceptsModel("deepseek-flash")).isTrue();
        assertThat(provider.acceptsModel(" DeepSeek-V4-Pro ")).isTrue();
        assertThat(provider.acceptsModel("deepseek-v4-flash")).isTrue();
        assertThat(provider.acceptsModel("gpt-5")).isFalse();
        assertThat(provider.acceptsModel(null)).isFalse();
    }

    @Test
    void mapsThinkingTogglesAndEffortForChatCompletions() {
        var profile = new DeepSeekChatCompletionsProfile();

        assertThat(profile.reasoningBodyParams(false, "high")).isEmpty();
        assertThat(profile.reasoningBodyParams(true, null)).isEmpty();
        assertThat(profile.reasoningBodyParams(true, "none"))
                .isEqualTo(Map.of("thinking", Map.of("type", "disabled")));
        assertThat(profile.reasoningBodyParams(true, "minimal"))
                .isEqualTo(Map.of(
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "low"));
        assertThat(profile.reasoningBodyParams(true, "medium"))
                .isEqualTo(Map.of(
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "high"));
        assertThat(profile.reasoningBodyParams(true, "ultra"))
                .isEqualTo(Map.of(
                        "thinking", Map.of("type", "enabled"),
                        "reasoning_effort", "max"));
    }

    @Test
    void mapsThinkingTogglesAndEffortForAnthropicMessages() {
        var profile = new DeepSeekMessagesProfile();

        assertThat(profile.reasoningBodyParams(true, "none"))
                .isEqualTo(Map.of("thinking", Map.of("type", "disabled")));
        assertThat(profile.reasoningBodyParams(true, "high"))
                .isEqualTo(Map.of(
                        "thinking", Map.of("type", "enabled"),
                        "output_config", Map.of("effort", "high")));
        assertThat(profile.reasoningBodyParams(false, "high")).isEmpty();
    }

    @Test
    void mapsReasoningEffortForResponses() {
        var profile = new DeepSeekResponsesProfile();

        assertThat(profile.reasoningEffort(true, "none")).isEqualTo("none");
        assertThat(profile.reasoningEffort(true, "low")).isEqualTo("low");
        assertThat(profile.reasoningEffort(true, "max")).isEqualTo("max");
        assertThat(profile.reasoningEffort(false, "max")).isNull();
        assertThat(profile.reasoningEffort(true, null)).isNull();
    }

    private DeepSeekProvider provider() {
        return new DeepSeekProvider(http);
    }

    private static AiChannelEntity channel(String baseUrl) {
        var channel = new AiChannelEntity();
        channel.setProvider("deepseek");
        channel.setProtocol("chat-completions");
        channel.setBaseUrl(baseUrl);
        channel.setTimeoutMs(30_000);
        return channel;
    }

}
