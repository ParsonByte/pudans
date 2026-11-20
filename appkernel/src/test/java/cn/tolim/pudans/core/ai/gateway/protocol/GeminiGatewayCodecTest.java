package cn.tolim.pudans.core.ai.gateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.core.extension.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatResponse;
import cn.tolim.pudans.core.ai.gateway.AiStreamEvent;
import cn.tolim.pudans.core.ai.gateway.AiTokenUsage;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeminiGatewayCodecTest {

    private static final String MODEL = "gemini-test";

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final GeminiGatewayCodec codec = new GeminiGatewayCodec(mapper);

    @Test
    void parsesTheRequestIntoTheChatIr() {
        var ir = codec.parse("""
                {
                  "systemInstruction": {"parts": [{"text": "be nice"}, {"text": "be brief"}]},
                  "contents": [
                    {"role": "user", "parts": [
                      {"text": "hi"},
                      {"inlineData": {"mimeType": "image/png", "data": "AAAA"}},
                      {"fileData": {"mimeType": "application/pdf", "fileUri": "gs://bucket/a.pdf"}},
                      {"functionResponse": {"name": "get_weather", "response": {"ok": true}}}
                    ]},
                    {"role": "model", "parts": [
                      {"text": "plan", "thought": true},
                      {"text": "hello"},
                      {"functionCall": {"name": "get_weather", "args": {"city": "Paris"}}}
                    ]}
                  ],
                  "generationConfig": {
                    "temperature": 0.4,
                    "top_p": 0.8,
                    "top_k": 5,
                    "max_output_tokens": 64,
                    "stopSequences": ["END"],
                    "responseMimeType": "application/json"
                  },
                  "tools": [{"functionDeclarations": [{
                    "name": "get_weather",
                    "description": "weather",
                    "parameters": {"type": "object"}
                  }]}]
                }
                """, MODEL, true);

        assertThat(ir.stream()).isTrue();
        assertThat(ir.messages()).hasSize(3);
        assertThat(ir.messages().getFirst().role()).isEqualTo("system");
        assertThat(((AiContentPart.Text) ir.messages().getFirst().parts().getFirst()).text())
                .isEqualTo("be nice\nbe brief");

        var user = ir.messages().get(1);
        assertThat(user.role()).isEqualTo("user");
        assertThat(user.parts().get(0)).isEqualTo(new AiContentPart.Text("hi", null));
        assertThat(user.parts().get(1)).isEqualTo(new AiContentPart.Image("image/png", "AAAA", null, null, null));
        assertThat(user.parts().get(2)).isEqualTo(
                new AiContentPart.File("application/pdf", null, "gs://bucket/a.pdf", null, null, null));
        var toolResult = (AiContentPart.ToolResult) user.parts().get(3);
        assertThat(toolResult.content().get("ok").asBoolean()).isTrue();

        var model = ir.messages().get(2);
        assertThat(model.role()).isEqualTo("assistant");
        assertThat(model.parts().get(0)).isEqualTo(new AiContentPart.Thinking("plan", null, null));
        assertThat(model.parts().get(1)).isEqualTo(new AiContentPart.Text("hello", null));
        var toolCall = (AiContentPart.ToolCall) model.parts().get(2);
        assertThat(toolCall.name()).isEqualTo("get_weather");
        assertThat(toolCall.arguments().get("city").asText()).isEqualTo("Paris");

        var params = ir.requestParams();
        assertThat(params.model()).isEqualTo(MODEL);
        assertThat(params.temperature()).isEqualTo(0.4);
        assertThat(params.topP()).isEqualTo(0.8);
        assertThat(params.topK()).isEqualTo(5);
        assertThat(params.maxTokens()).isEqualTo(64);
        assertThat(params.stop()).containsExactly("END");
        assertThat(params.extra().get("response_format").get("type").asText()).isEqualTo("json_object");

        assertThat(ir.tools()).hasSize(1);
        assertThat(ir.tools().getFirst().name()).isEqualTo("get_weather");
        assertThat(ir.tools().getFirst().description()).isEqualTo("weather");
        assertThat(ir.tools().getFirst().parameters().get("type").asText()).isEqualTo("object");
    }

    @Test
    void parsesCamelCaseGenerationConfigAndDefaultsMissingParts() {
        var ir = codec.parse("""
                {"contents": [{"parts": [{"text": "hi"}]}],
                 "generationConfig": {"topP": 0.3, "topK": 2, "maxOutputTokens": 8}}
                """, MODEL, false);

        var user = ir.messages().getFirst();
        assertThat(user.role()).isEqualTo("user");
        assertThat(ir.requestParams().topP()).isEqualTo(0.3);
        assertThat(ir.requestParams().topK()).isEqualTo(2);
        assertThat(ir.requestParams().maxTokens()).isEqualTo(8);
        assertThat(ir.requestParams().stop()).isEmpty();
        assertThat(ir.requestParams().extra()).isEmpty();
        assertThat(ir.tools()).isEmpty();
        assertThat(ir.stream()).isFalse();
    }

    @Test
    void rejectsABodyWithoutContentsOrWithUnnamedTools() {
        assertThatThrownBy(() -> codec.parse("{}", MODEL, false))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("contents");
        assertThatThrownBy(() -> codec.parse("""
                {"contents": [], "tools": [{"functionDeclarations": [{"description": "x"}]}]}
                """, MODEL, false))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("name");
        assertThatThrownBy(() -> codec.parse("not-json", MODEL, false))
                .isInstanceOf(AiProtocolException.class);
    }

    @Test
    void encodesTheResponseAsGeminiCandidates() throws JsonProcessingException {
        var message = new AiChatMessage("assistant", List.of(
                new AiContentPart.Text("pong", null),
                new AiContentPart.Thinking("plan", null, null),
                new AiContentPart.ToolCall(
                        "call_1", "get_weather", mapper.readTree("{\"city\":\"Paris\"}"), null)));

        var root = json(codec.encode(
                new AiChatResponse("resp-1", "upstream", message, new AiTokenUsage(11, 7, 3, null), "length"),
                MODEL));

        assertThat(root.get("modelVersion").asText()).isEqualTo(MODEL);
        var candidate = root.get("candidates").get(0);
        assertThat(candidate.get("index").asInt()).isZero();
        assertThat(candidate.get("finishReason").asText()).isEqualTo("MAX_TOKENS");
        var content = candidate.get("content");
        assertThat(content.get("role").asText()).isEqualTo("model");
        var parts = content.get("parts");
        assertThat(parts.get(0).get("text").asText()).isEqualTo("pong");
        assertThat(parts.get(1).get("text").asText()).isEqualTo("plan");
        assertThat(parts.get(1).get("thought").asBoolean()).isTrue();
        assertThat(parts.get(2).get("functionCall").get("name").asText()).isEqualTo("get_weather");
        assertThat(parts.get(2).get("functionCall").get("args").get("city").asText()).isEqualTo("Paris");
        var usage = root.get("usageMetadata");
        assertThat(usage.get("promptTokenCount").asInt()).isEqualTo(11);
        assertThat(usage.get("candidatesTokenCount").asInt()).isEqualTo(7);
        assertThat(usage.get("totalTokenCount").asInt()).isEqualTo(18);
        assertThat(usage.get("cachedContentTokenCount").asInt()).isEqualTo(3);
    }

    @Test
    void encodesDefaultsWhenTheResponseCarriesNothing() throws JsonProcessingException {
        var root = json(codec.encode(new AiChatResponse(null, null, null, null, null), MODEL));

        assertThat(root.get("modelVersion").asText()).isEqualTo(MODEL);
        var candidate = root.get("candidates").get(0);
        assertThat(candidate.get("content").get("parts").size()).isZero();
        assertThat(candidate.get("finishReason").asText()).isEqualTo("STOP");
        assertThat(root.has("usageMetadata")).isFalse();
    }

    @Test
    void streamsGeminiSseEvents() throws JsonProcessingException {
        var encoder = codec.stream("resp-1", MODEL, new AiTokenUsage(5, 9, 2, null));

        var start = sseEvents(encoder.start());
        assertThat(start).hasSize(1);
        var startChunk = data(start.getFirst());
        assertThat(startChunk.get("modelVersion").asText()).isEqualTo(MODEL);
        var startPart = startChunk.get("candidates").get(0).get("content").get("parts").get(0);
        assertThat(startPart.get("text").asText()).isEmpty();
        assertThat(startChunk.get("candidates").get(0).get("content").get("role").asText())
                .isEqualTo("model");

        var text = sseEvents(encoder.event(new AiStreamEvent("Hello", null, null, null, null)));
        assertThat(data(text.getFirst()).get("candidates").get(0).get("content").get("parts").get(0)
                .get("text").asText()).isEqualTo("Hello");

        var reasoning = sseEvents(encoder.event(new AiStreamEvent(null, "plan", null, null, null)));
        var thought = data(reasoning.getFirst()).get("candidates").get(0).get("content").get("parts").get(0);
        assertThat(thought.get("text").asText()).isEqualTo("plan");
        assertThat(thought.get("thought").asBoolean()).isTrue();

        var tool = sseEvents(encoder.event(new AiStreamEvent(
                null, null, new AiStreamEvent.ToolCallDelta(0, "call_1", "get_weather", "{\"city\":\"Paris\"}"),
                null, null)));
        var functionCall = data(tool.getFirst()).get("candidates").get(0).get("content").get("parts").get(0)
                .get("functionCall");
        assertThat(functionCall.get("name").asText()).isEqualTo("get_weather");
        assertThat(functionCall.get("args").get("city").asText()).isEqualTo("Paris");

        assertThat(encoder.event(new AiStreamEvent(
                null, null, null, new AiTokenUsage(5, 9, 2, null), null))).isEmpty();

        var finish = sseEvents(encoder.finish(new AiStreamEvent(
                null, null, null, new AiTokenUsage(6, 10, 3, null), "length")));
        assertThat(finish).hasSize(1);
        var finalChunk = data(finish.getFirst());
        assertThat(finalChunk.get("candidates").get(0).get("finishReason").asText()).isEqualTo("MAX_TOKENS");
        var usage = finalChunk.get("usageMetadata");
        assertThat(usage.get("promptTokenCount").asInt()).isEqualTo(6);
        assertThat(usage.get("candidatesTokenCount").asInt()).isEqualTo(10);
        assertThat(usage.get("totalTokenCount").asInt()).isEqualTo(16);
        assertThat(usage.get("cachedContentTokenCount").asInt()).isEqualTo(3);
    }

    @Test
    void streamFinishFallsBackToTheInitialUsageAndStopReason() throws JsonProcessingException {
        var encoder = codec.stream("resp-1", MODEL, new AiTokenUsage(2, 3, null, null));

        var finish = sseEvents(encoder.finish(null));

        var chunk = data(finish.getFirst());
        assertThat(chunk.get("candidates").get(0).get("finishReason").asText()).isEqualTo("STOP");
        assertThat(chunk.get("usageMetadata").get("promptTokenCount").asInt()).isEqualTo(2);
        assertThat(chunk.get("usageMetadata").get("candidatesTokenCount").asInt()).isEqualTo(3);
    }

    @Test
    void exposesTheGeminiProtocol() {
        assertThat(codec.protocol()).isEqualTo(AiProvider.Protocol.GEMINI);
    }

    private JsonNode json(String body) throws JsonProcessingException {
        return mapper.readTree(body);
    }

    private JsonNode data(SseEvent event) throws JsonProcessingException {
        return mapper.readTree(event.data());
    }

    private static List<SseEvent> sseEvents(String body) {
        assertThat(body).isNotBlank().endsWith("\n\n");
        var events = new ArrayList<SseEvent>();
        for (var block : body.split("\n\n")) {
            if (block.isBlank()) {
                continue;
            }
            assertThat(block).startsWith("data: ");
            events.add(new SseEvent(block.substring("data: ".length())));
        }
        return events;
    }

    private record SseEvent(String data) {}
}