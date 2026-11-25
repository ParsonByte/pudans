package cn.tolim.pudans.core.ai.protocol.responses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.core.ai.protocol.AiChatProtocolRequest;
import cn.tolim.pudans.infra.exception.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.URLSource;
import io.agentscope.core.message.VideoBlock;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolChoice;
import io.agentscope.core.model.ToolSchema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class OpenAiResponsesCodecTest {

    private final OpenAiResponsesCodec codec = new OpenAiResponsesCodec(new StandardResponsesProfile());

    @Test
    void encodesStandardRequestWithoutProviderSpecificFields() {
        var messages = List.of(
                Msg.builder().role(MsgRole.SYSTEM).textContent("system prompt").build(),
                Msg.builder().role(MsgRole.USER).textContent("hello").build());
        var tool = ToolSchema.builder()
                .name("lookup")
                .description("lookup")
                .parameters(Map.of("type", "object", "properties", Map.of()))
                .build();
        var options = GenerateOptions.builder()
                .temperature(0.5)
                .topP(0.9)
                .reasoningEffort("medium")
                .parallelToolCalls(true)
                .toolChoice(new ToolChoice.Auto())
                .build();

        var body = codec.request("model-x", messages, List.of(tool), options, 1_024);

        assertThat(body)
                .containsEntry("model", "model-x")
                .containsEntry("stream", true)
                .containsEntry("temperature", 0.5)
                .containsEntry("top_p", 0.9)
                .containsEntry("parallel_tool_calls", true)
                .containsEntry("tool_choice", "auto")
                .containsEntry("reasoning", Map.of("effort", "medium"))
                .doesNotContainKey("store");
        assertThat(body.get("instructions")).isEqualTo("system prompt");
        assertThat((List<?>) body.get("input")).hasSize(1);
        assertThat((List<?>) body.get("tools")).hasSize(1);
    }

    @Test
    void appliesResponsesProfileCapabilities() {
        var profile = new ResponsesProfile() {

            @Override
            public Map<String, Object> mediaItem(String mediaType, String mediaData) {
                return Map.of("type", "input_video", "video_url", mediaData);
            }

            @Override
            public Set<String> unsupportedBodyFields() {
                return Set.of("parallel_tool_calls");
            }

            @Override
            public Object toolChoice(ToolChoice choice) {
                return "required";
            }

            @Override
            public Boolean store() {
                return false;
            }

            @Override
            public void customizeBody(Map<String, Object> body) {
                body.put("profile_flag", true);
            }
        };
        var messages = List.of(Msg.builder()
                .role(MsgRole.USER)
                .content(
                        TextBlock.builder().text("watch").build(),
                        VideoBlock.builder()
                                .source(URLSource.builder()
                                        .url("https://example.com/v.mp4")
                                        .mimeType("video/mp4")
                                        .build())
                                .build())
                .build());
        var options = GenerateOptions.builder()
                .parallelToolCalls(true)
                .toolChoice(new ToolChoice.Auto())
                .build();

        var body = new OpenAiResponsesCodec(profile)
                .request("model-x", messages, List.of(), options, 10);

        assertThat(body)
                .containsEntry("store", false)
                .containsEntry("profile_flag", true)
                .containsEntry("tool_choice", "required")
                .doesNotContainKey("parallel_tool_calls");
        var input = (List<?>) body.get("input");
        assertThat(input).hasSize(1);
        assertThat(input.get(0)).isEqualTo(Map.of(
                "role",
                "user",
                "content",
                List.of(
                        Map.of("type", "input_text", "text", "watch"),
                        Map.of(
                                "type",
                                "input_video",
                                "video_url",
                                "https://example.com/v.mp4"))));
    }

    @Test
    void rejectsUnsupportedMediaThroughProfile() {
        var profile = new ResponsesProfile() {

            @Override
            public Map<String, Object> mediaItem(String mediaType, String mediaData) {
                if (mediaType.startsWith("video/")) {
                    throw ResponsesProfile.unsupportedMedia("自定义协议不支持视频输入。");
                }
                return null;
            }
        };
        var messages = List.of(Msg.builder()
                .role(MsgRole.USER)
                .content(VideoBlock.builder()
                        .source(URLSource.builder()
                                .url("https://example.com/v.mp4")
                                .mimeType("video/mp4")
                                .build())
                        .build())
                .build());

        assertThatThrownBy(() -> new OpenAiResponsesCodec(profile)
                        .request("model-x", messages, List.of(), null, 10))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("视频");
    }

    @Test
    void replaysOnlyProviderOutputItemsAllowedByProfile() {
        var assistant = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("answer")
                .metadata(Map.of(
                        "providerOutputItems",
                        List.of(
                                Map.of("type", "reasoning", "id", "reasoning-1"),
                                Map.of("type", "web_search_call", "id", "search-1"),
                                Map.of("type", "message", "id", "message-1"))))
                .build();
        var profile = new ResponsesProfile() {

            @Override
            public Set<String> replayedItemTypes() {
                return Set.of("reasoning", "web_search_call");
            }
        };

        var standardBody = codec.request("model-x", List.of(assistant), List.of(), null, 10);
        var customBody = new OpenAiResponsesCodec(profile)
                .request("model-x", List.of(assistant), List.of(), null, 10);

        assertThat((List<?>) standardBody.get("input")).hasSize(2);
        assertThat((List<?>) customBody.get("input")).hasSize(3);
    }

    @Test
    void decodesStandardEventStream() {
        var decoder = codec.streamDecoder("model-x", "provider-x");

        decoder.accept("{\"type\":\"response.created\",\"response\":{\"id\":\"resp_1\"}}");
        var delta = decoder.accept("{\"type\":\"response.output_text.delta\",\"delta\":\"hello\"}");
        var completed = decoder.accept("{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_1\","
                + "\"status\":\"completed\",\"usage\":{\"input_tokens\":3,\"output_tokens\":2,"
                + "\"input_tokens_details\":{\"cached_tokens\":1}},\"output\":[]}}");

        assertThat(delta).hasSize(1);
        assertThat(delta.get(0).getContent()).hasSize(1);
        assertThat(delta.get(0).getContent().get(0)).isInstanceOf(TextBlock.class);
        assertThat(((TextBlock) delta.get(0).getContent().get(0)).getText()).isEqualTo("hello");
        assertThat(completed).hasSize(1);
        assertThat(completed.get(0).getFinishReason()).isEqualTo("stop");
        assertThat(completed.get(0).getUsage().getInputTokens()).isEqualTo(3);
        assertThat(completed.get(0).getUsage().getOutputTokens()).isEqualTo(2);
        assertThat(completed.get(0).getUsage().getCachedTokens()).isEqualTo(1);
    }

    @Test
    void appliesProfileEventType() {
        var profile = new ResponsesProfile() {

            @Override
            public String eventType(JsonNode event) {
                var type = event.path("type").asText("");
                return type.startsWith("custom.") ? type.substring("custom.".length()) : type;
            }
        };
        var decoder = new OpenAiResponsesCodec(profile).streamDecoder("model-x", "provider-x");

        var delta = decoder.accept("{\"type\":\"custom.response.output_text.delta\",\"delta\":\"hi\"}");

        assertThat(delta).hasSize(1);
        assertThat(delta.get(0).getContent()).hasSize(1);
    }

    @Test
    void createsResponsesModelWithProviderProfile() {
        var request = new AiChatProtocolRequest(
                "custom",
                "model-x",
                "secret",
                "https://api.example.com",
                "/responses",
                Map.of(),
                30_000,
                32_000,
                4_096,
                false,
                false,
                Map.of());

        var model = new ResponsesProtocol().create(request, new StandardResponsesProfile());

        assertThat(model).isInstanceOf(OpenAiResponsesModel.class);
        assertThat(model.getModelName()).isEqualTo("model-x");
    }
}
