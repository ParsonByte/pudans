package cn.tolim.pudans.core.ai.gateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.resource.AiProvider;
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

class AiResponseEncoderTest {

    private static final String MODEL = "pudans-test";

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AiResponseEncoders encoders = new AiResponseEncoders(mapper, new GeminiGatewayCodec(mapper));

    @Test
    void encodesOpenAiChatCompletion() throws JsonProcessingException {
        var arguments = mapper.readTree("{\"city\":\"Paris\"}");
        var chatResponse = response(
                "tool_calls",
                List.of(
                        new AiContentPart.Thinking("plan", null, null),
                        new AiContentPart.Text("Checking.", null),
                        new AiContentPart.ToolCall("call_1", "get_weather", arguments, null)),
                new AiTokenUsage(11, 7, 3, 2));

        var root = json(encoders.require(AiProvider.Protocol.OPENAI_CHAT).encode(chatResponse, MODEL));

        assertThat(root.get("id").asText()).isEqualTo("resp_1");
        assertThat(root.get("object").asText()).isEqualTo("chat.completion");
        assertThat(root.get("created").asLong()).isPositive();
        assertThat(root.get("model").asText()).isEqualTo(MODEL);
        var choice = root.get("choices").get(0);
        assertThat(choice.get("index").asInt()).isZero();
        assertThat(choice.get("finish_reason").asText()).isEqualTo("tool_calls");
        var message = choice.get("message");
        assertThat(message.get("role").asText()).isEqualTo("assistant");
        assertThat(message.get("content").asText()).isEqualTo("Checking.");
        assertThat(message.get("reasoning_content").asText()).isEqualTo("plan");
        var toolCall = message.get("tool_calls").get(0);
        assertThat(toolCall.get("id").asText()).isEqualTo("call_1");
        assertThat(toolCall.get("type").asText()).isEqualTo("function");
        assertThat(toolCall.get("function").get("name").asText()).isEqualTo("get_weather");
        assertThat(toolCall.get("function").get("arguments").asText()).isEqualTo("{\"city\":\"Paris\"}");
        var usage = root.get("usage");
        assertThat(usage.get("prompt_tokens").asInt()).isEqualTo(11);
        assertThat(usage.get("completion_tokens").asInt()).isEqualTo(7);
        assertThat(usage.get("total_tokens").asInt()).isEqualTo(18);
        assertThat(usage.get("prompt_tokens_details").get("cached_tokens").asInt()).isEqualTo(3);
    }

    @Test
    void encodesOpenAiStreamWithTextReasoningToolCallAndUsage() throws JsonProcessingException {
        var encoder = encoders.require(AiProvider.Protocol.OPENAI_CHAT)
                .stream("resp_1", MODEL, new AiTokenUsage(5, 9, 2, null));

        var start = sseEvents(encoder.start());
        assertThat(start).hasSize(1);
        var startChunk = data(start.get(0));
        assertThat(startChunk.get("object").asText()).isEqualTo("chat.completion.chunk");
        assertThat(startChunk.get("model").asText()).isEqualTo(MODEL);
        var startDelta = startChunk.get("choices").get(0).get("delta");
        assertThat(startDelta.get("role").asText()).isEqualTo("assistant");
        assertThat(startDelta.get("content").asText()).isEmpty();
        assertThat(startChunk.get("choices").get(0).get("finish_reason").isNull()).isTrue();

        var content = sseEvents(encoder.event(new AiStreamEvent("Hello", null, null, null, null)));
        assertThat(content).hasSize(1);
        var contentDelta = data(content.get(0)).get("choices").get(0).get("delta");
        assertThat(contentDelta.get("content").asText()).isEqualTo("Hello");
        assertThat(data(content.get(0)).get("choices").get(0).get("finish_reason").isNull()).isTrue();

        var reasoning = sseEvents(encoder.event(new AiStreamEvent(null, "thinking", null, null, null)));
        var reasoningDelta = data(reasoning.get(0)).get("choices").get(0).get("delta");
        assertThat(reasoningDelta.get("reasoning_content").asText()).isEqualTo("thinking");

        var streamingToolCall =
                new AiStreamEvent.ToolCallDelta(0, "call_1", "get_weather", "{\"city\":\"Paris\"}");
        var tool = sseEvents(encoder.event(new AiStreamEvent(null, null, streamingToolCall, null, null)));
        var toolDelta = data(tool.get(0)).get("choices").get(0).get("delta").get("tool_calls").get(0);
        assertThat(toolDelta.get("index").asInt()).isZero();
        assertThat(toolDelta.get("id").asText()).isEqualTo("call_1");
        assertThat(toolDelta.get("type").asText()).isEqualTo("function");
        assertThat(toolDelta.get("function").get("name").asText()).isEqualTo("get_weather");
        assertThat(toolDelta.get("function").get("arguments").asText()).isEqualTo("{\"city\":\"Paris\"}");

        var finish = sseEvents(encoder.finish(
                new AiStreamEvent(null, null, null, new AiTokenUsage(5, 9, 2, 0), "stop")));
        assertThat(finish).hasSize(2);
        var finalChunk = data(finish.get(0));
        assertThat(finalChunk.get("choices").get(0).get("delta").size()).isZero();
        assertThat(finalChunk.get("choices").get(0).get("finish_reason").asText()).isEqualTo("stop");
        assertThat(finalChunk.get("usage").get("prompt_tokens").asInt()).isEqualTo(5);
        assertThat(finalChunk.get("usage").get("completion_tokens").asInt()).isEqualTo(9);
        assertThat(finalChunk.get("usage").get("total_tokens").asInt()).isEqualTo(14);
        assertThat(finalChunk.get("usage").get("prompt_tokens_details").get("cached_tokens").asInt())
                .isEqualTo(2);
        assertThat(finish.get(1).data()).isEqualTo("[DONE]");
    }

    @Test
    void omitsAbsentOpenAiStreamFieldsAndDefaultsFinish() throws JsonProcessingException {
        var encoder = encoders.require(AiProvider.Protocol.OPENAI_CHAT).stream("resp_1", MODEL, null);

        assertThat(encoder.event(new AiStreamEvent(
                null, null, null, new AiTokenUsage(1, 2, null, null), null))).isEmpty();
        assertThat(encoder.event(new AiStreamEvent(null, null, null, null, "stop"))).isEmpty();

        var tool = sseEvents(encoder.event(new AiStreamEvent(
                null, null, new AiStreamEvent.ToolCallDelta(null, null, null, "{\"city\":"), null, null)));
        var call = data(tool.get(0)).get("choices").get(0).get("delta").get("tool_calls").get(0);
        assertThat(call.has("index")).isFalse();
        assertThat(call.has("id")).isFalse();
        assertThat(call.get("function").has("name")).isFalse();
        assertThat(call.get("function").get("arguments").asText()).isEqualTo("{\"city\":");

        var finish = sseEvents(encoder.finish(null));
        var finalChunk = data(finish.get(0));
        assertThat(finalChunk.get("choices").get(0).get("finish_reason").asText()).isEqualTo("stop");
        assertThat(finalChunk.get("usage").get("prompt_tokens").asInt()).isZero();
        assertThat(finalChunk.get("usage").get("completion_tokens").asInt()).isZero();
        assertThat(finalChunk.get("usage").get("total_tokens").asInt()).isZero();
    }

    @Test
    void encodesOpenAiDefaultsForMissingValues() throws JsonProcessingException {
        var root = json(encoders.require(AiProvider.Protocol.OPENAI_CHAT)
                .encode(new AiChatResponse(null, null, null, null, null), null));

        assertThat(root.get("id").asText()).isEmpty();
        assertThat(root.get("model").asText()).isEmpty();
        assertThat(root.get("choices").get(0).get("finish_reason").asText()).isEqualTo("stop");
        var message = root.get("choices").get(0).get("message");
        assertThat(message.get("content").asText()).isEmpty();
        assertThat(message.has("reasoning_content")).isFalse();
        assertThat(message.has("tool_calls")).isFalse();
        assertThat(root.get("usage").get("prompt_tokens").asInt()).isZero();
        assertThat(root.get("usage").get("prompt_tokens_details").get("cached_tokens").asInt()).isZero();
    }

    @Test
    void encodesAnthropicMessageWithContentBlocks() throws JsonProcessingException {
        var chatResponse = response(
                "stop",
                List.of(
                        new AiContentPart.Thinking("plan", "sig-1", null),
                        new AiContentPart.Text("Checking.", null),
                        new AiContentPart.ToolCall(
                                "toolu_1", "get_weather", mapper.readTree("{\"city\":\"Paris\"}"), null),
                        new AiContentPart.Image("image/png", "AAAA", null, null, null),
                        new AiContentPart.File("application/pdf", "BBBB", null, "a.pdf", null, null),
                        new AiContentPart.ToolResult(
                                "toolu_1", mapper.getNodeFactory().textNode("sunny"), null, null),
                        new AiContentPart.Custom("unknown", mapper.createObjectNode())),
                new AiTokenUsage(11, 7, 3, 2));

        var root = json(encoders.require(AiProvider.Protocol.ANTHROPIC).encode(chatResponse, MODEL));

        assertThat(root.get("id").asText()).isEqualTo("resp_1");
        assertThat(root.get("type").asText()).isEqualTo("message");
        assertThat(root.get("role").asText()).isEqualTo("assistant");
        assertThat(root.get("model").asText()).isEqualTo(MODEL);
        assertThat(root.get("stop_reason").asText()).isEqualTo("end_turn");
        assertThat(root.get("stop_sequence").isNull()).isTrue();
        var content = root.get("content");
        assertThat(content.size()).isEqualTo(3);
        assertThat(content.get(0).get("type").asText()).isEqualTo("thinking");
        assertThat(content.get(0).get("thinking").asText()).isEqualTo("plan");
        assertThat(content.get(0).get("signature").asText()).isEqualTo("sig-1");
        assertThat(content.get(1).get("type").asText()).isEqualTo("text");
        assertThat(content.get(1).get("text").asText()).isEqualTo("Checking.");
        assertThat(content.get(2).get("type").asText()).isEqualTo("tool_use");
        assertThat(content.get(2).get("id").asText()).isEqualTo("toolu_1");
        assertThat(content.get(2).get("name").asText()).isEqualTo("get_weather");
        assertThat(content.get(2).get("input").get("city").asText()).isEqualTo("Paris");
        var usage = root.get("usage");
        assertThat(usage.get("input_tokens").asInt()).isEqualTo(11);
        assertThat(usage.get("output_tokens").asInt()).isEqualTo(7);
        assertThat(usage.get("cache_read_input_tokens").asInt()).isEqualTo(3);
        assertThat(usage.get("cache_creation_input_tokens").asInt()).isEqualTo(2);
    }

    @Test
    void mapsAnthropicStopReasons() throws JsonProcessingException {
        var encoder = encoders.require(AiProvider.Protocol.ANTHROPIC);
        var message = new AiChatMessage("assistant", List.of(new AiContentPart.Text("hi", null)));

        assertThat(json(encoder.encode(new AiChatResponse("r", "m", message, null, "length"), MODEL))
                .get("stop_reason").asText()).isEqualTo("max_tokens");
        assertThat(json(encoder.encode(new AiChatResponse("r", "m", message, null, "tool_calls"), MODEL))
                .get("stop_reason").asText()).isEqualTo("tool_use");
        assertThat(json(encoder.encode(new AiChatResponse("r", "m", message, null, null), MODEL))
                .get("stop_reason").asText()).isEqualTo("end_turn");
    }

    @Test
    void encodesAnthropicStreamStartAndText() throws JsonProcessingException {
        var encoder = encoders.require(AiProvider.Protocol.ANTHROPIC)
                .stream("msg_1", MODEL, new AiTokenUsage(12, 4, 6, 8));

        var start = sseEvents(encoder.start());
        assertThat(start).hasSize(2);
        assertThat(start.get(0).name()).isEqualTo("message_start");
        var messageStart = data(start.get(0));
        assertThat(messageStart.get("type").asText()).isEqualTo("message_start");
        var message = messageStart.get("message");
        assertThat(message.get("id").asText()).isEqualTo("msg_1");
        assertThat(message.get("type").asText()).isEqualTo("message");
        assertThat(message.get("role").asText()).isEqualTo("assistant");
        assertThat(message.get("model").asText()).isEqualTo(MODEL);
        assertThat(message.get("content").size()).isZero();
        assertThat(message.get("usage").get("input_tokens").asInt()).isEqualTo(12);
        assertThat(message.get("usage").get("output_tokens").asInt()).isZero();
        assertThat(message.get("usage").get("cache_read_input_tokens").asInt()).isEqualTo(6);
        assertThat(message.get("usage").get("cache_creation_input_tokens").asInt()).isEqualTo(8);

        assertThat(start.get(1).name()).isEqualTo("content_block_start");
        var blockStart = data(start.get(1));
        assertThat(blockStart.get("type").asText()).isEqualTo("content_block_start");
        assertThat(blockStart.get("index").asInt()).isZero();
        assertThat(blockStart.get("content_block").get("type").asText()).isEqualTo("text");
        assertThat(blockStart.get("content_block").get("text").asText()).isEmpty();

        var delta = sseEvents(encoder.event(new AiStreamEvent("Hello", null, null, null, null)));
        assertThat(delta).hasSize(1);
        assertThat(delta.get(0).name()).isEqualTo("content_block_delta");
        var deltaData = data(delta.get(0));
        assertThat(deltaData.get("type").asText()).isEqualTo("content_block_delta");
        assertThat(deltaData.get("index").asInt()).isZero();
        assertThat(deltaData.get("delta").get("type").asText()).isEqualTo("text_delta");
        assertThat(deltaData.get("delta").get("text").asText()).isEqualTo("Hello");
    }

    @Test
    void encodesAnthropicStreamBlockSwitchesAndFinish() throws JsonProcessingException {
        var encoder = encoders.require(AiProvider.Protocol.ANTHROPIC)
                .stream("msg_1", MODEL, new AiTokenUsage(12, 4, 6, 8));
        encoder.start();

        var reasoning = sseEvents(encoder.event(new AiStreamEvent(null, "plan", null, null, null)));
        assertThat(reasoning).hasSize(3);
        assertThat(reasoning.get(0).name()).isEqualTo("content_block_stop");
        assertThat(data(reasoning.get(0)).get("index").asInt()).isZero();
        assertThat(reasoning.get(1).name()).isEqualTo("content_block_start");
        assertThat(data(reasoning.get(1)).get("index").asInt()).isEqualTo(1);
        assertThat(data(reasoning.get(1)).get("content_block").get("type").asText()).isEqualTo("thinking");
        assertThat(reasoning.get(2).name()).isEqualTo("content_block_delta");
        var thinkingDelta = data(reasoning.get(2)).get("delta");
        assertThat(thinkingDelta.get("type").asText()).isEqualTo("thinking_delta");
        assertThat(thinkingDelta.get("thinking").asText()).isEqualTo("plan");

        var toolStart = sseEvents(encoder.event(new AiStreamEvent(
                null, null, new AiStreamEvent.ToolCallDelta(0, "toolu_1", "get_weather", null), null, null)));
        assertThat(toolStart).hasSize(2);
        assertThat(toolStart.get(0).name()).isEqualTo("content_block_stop");
        assertThat(data(toolStart.get(0)).get("index").asInt()).isEqualTo(1);
        assertThat(toolStart.get(1).name()).isEqualTo("content_block_start");
        var toolBlock = data(toolStart.get(1)).get("content_block");
        assertThat(data(toolStart.get(1)).get("index").asInt()).isEqualTo(2);
        assertThat(toolBlock.get("type").asText()).isEqualTo("tool_use");
        assertThat(toolBlock.get("id").asText()).isEqualTo("toolu_1");
        assertThat(toolBlock.get("name").asText()).isEqualTo("get_weather");
        assertThat(toolBlock.get("input").isObject()).isTrue();

        var toolArguments = sseEvents(encoder.event(new AiStreamEvent(
                null, null, new AiStreamEvent.ToolCallDelta(0, null, null, "{\"city\":\"Paris\"}"), null, null)));
        assertThat(toolArguments).hasSize(1);
        assertThat(toolArguments.get(0).name()).isEqualTo("content_block_delta");
        var inputDelta = data(toolArguments.get(0));
        assertThat(inputDelta.get("index").asInt()).isEqualTo(2);
        assertThat(inputDelta.get("delta").get("type").asText()).isEqualTo("input_json_delta");
        assertThat(inputDelta.get("delta").get("partial_json").asText()).isEqualTo("{\"city\":\"Paris\"}");

        var finish = sseEvents(encoder.finish(
                new AiStreamEvent(null, null, null, new AiTokenUsage(12, 9, 6, 8), "tool_calls")));
        assertThat(finish).hasSize(3);
        assertThat(finish.get(0).name()).isEqualTo("content_block_stop");
        assertThat(data(finish.get(0)).get("index").asInt()).isEqualTo(2);
        assertThat(finish.get(1).name()).isEqualTo("message_delta");
        var messageDelta = data(finish.get(1));
        assertThat(messageDelta.get("type").asText()).isEqualTo("message_delta");
        assertThat(messageDelta.get("delta").get("stop_reason").asText()).isEqualTo("tool_use");
        assertThat(messageDelta.get("usage").get("output_tokens").asInt()).isEqualTo(9);
        assertThat(messageDelta.get("usage").get("cache_read_input_tokens").asInt()).isEqualTo(6);
        assertThat(messageDelta.get("usage").get("cache_creation_input_tokens").asInt()).isEqualTo(8);
        assertThat(finish.get(2).name()).isEqualTo("message_stop");
        assertThat(data(finish.get(2)).get("type").asText()).isEqualTo("message_stop");
    }

    @Test
    void requiresSupportedDownstreamProtocols() {
        assertThat(encoders.require(AiProvider.Protocol.OPENAI_CHAT).protocol())
                .isEqualTo(AiProvider.Protocol.OPENAI_CHAT);
        assertThat(encoders.require(AiProvider.Protocol.ANTHROPIC).protocol())
                .isEqualTo(AiProvider.Protocol.ANTHROPIC);
        assertThat(encoders.require(AiProvider.Protocol.GEMINI).protocol())
                .isEqualTo(AiProvider.Protocol.GEMINI);
        assertThatThrownBy(() -> encoders.require(AiProvider.Protocol.OPENAI_RESPONSES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPENAI_RESPONSES");
        assertThatThrownBy(() -> encoders.require(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AiChatResponse response(String finishReason, List<AiContentPart> parts, AiTokenUsage usage) {
        return new AiChatResponse(
                "resp_1", "upstream-model", new AiChatMessage("assistant", parts), usage, finishReason);
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
            String name = null;
            String data = null;
            for (var line : block.split("\n")) {
                if (line.startsWith("event: ")) {
                    name = line.substring("event: ".length());
                } else if (line.startsWith("data: ")) {
                    data = line.substring("data: ".length());
                }
            }
            assertThat(data).isNotNull();
            events.add(new SseEvent(name, data));
        }
        return events;
    }

    private record SseEvent(String name, String data) {}
}
