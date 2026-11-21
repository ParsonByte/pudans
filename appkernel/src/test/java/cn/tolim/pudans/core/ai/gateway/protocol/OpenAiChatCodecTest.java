package cn.tolim.pudans.core.ai.gateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.contract.infra.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OpenAiChatCodecTest {

    private static final String TEXT_AND_TOOL_CALL_BODY = """
            {
              "model": "gpt-4o-mini",
              "messages": [
                {"role": "system", "content": "You are helpful."},
                {"role": "user", "content": "What is the weather in Paris?"},
                {"role": "assistant", "content": "Let me check.", "reasoning_content": "I should check.",
                 "tool_calls": [
                  {"id": "call_1", "type": "function",
                   "function": {"name": "get_weather", "arguments": "{\\"city\\":\\"Paris\\"}"}}
                ]},
                {"role": "tool", "tool_call_id": "call_1", "content": "sunny, 20C"}
              ],
              "tools": [
                {"type": "function", "function": {
                  "name": "get_weather",
                  "description": "Get the current weather",
                  "parameters": {"type": "object", "properties": {"city": {"type": "string"}},
                                 "required": ["city"]}
                }}
              ],
              "temperature": 0.2,
              "max_tokens": 256,
              "stream_options": {"include_usage": true},
              "stream": false
            }
            """;

    private static final String UNKNOWN_BLOCK_BODY = """
            {
              "model": "gpt-4o-mini",
              "messages": [
                {"role": "user", "content": [
                  {"type": "text", "text": "hello"},
                  {"type": "input_audio", "input_audio": {"data": "UklGRg==", "format": "wav"}}
                ]}
              ],
              "stream": false
            }
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final OpenAiChatCodec codec = new OpenAiChatCodec(mapper);

    @Test
    void roundTripsTextAndToolCalls() throws JsonProcessingException {
        var ir = codec.parse(TEXT_AND_TOOL_CALL_BODY);

        assertThat(codec.protocol()).isEqualTo(AiProvider.Protocol.OPENAI_CHAT);
        assertThat(ir.stream()).isFalse();
        assertThat(ir.requestParams().model()).isEqualTo("gpt-4o-mini");
        assertThat(ir.requestParams().maxTokens()).isEqualTo(256);
        assertThat(ir.requestParams().extra()).containsKey("stream_options");
        assertThat(ir.tools()).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("get_weather");
            assertThat(tool.parameters().get("required").get(0).asText()).isEqualTo("city");
        });
        assertThat(ir.messages()).extracting(AiChatMessage::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(ir.messages().get(2).parts())
                .filteredOn(AiContentPart.Thinking.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking ->
                        assertThat(thinking.text()).isEqualTo("I should check."));
        assertThat(ir.messages().get(2).parts())
                .filteredOn(AiContentPart.ToolCall.class::isInstance)
                .singleElement()
                .satisfies(part -> {
                    var call = (AiContentPart.ToolCall) part;
                    assertThat(call.id()).isEqualTo("call_1");
                    assertThat(call.name()).isEqualTo("get_weather");
                    assertThat(call.arguments().get("city").asText()).isEqualTo("Paris");
                });
        assertThat(ir.messages().get(3).parts()).singleElement()
                .isInstanceOfSatisfying(AiContentPart.ToolResult.class, result -> {
                    assertThat(result.toolCallId()).isEqualTo("call_1");
                    assertThat(result.content().asText()).isEqualTo("sunny, 20C");
                });

        assertJsonRoundTrip(TEXT_AND_TOOL_CALL_BODY);
    }

    @Test
    void preservesUnknownContentBlocks() throws JsonProcessingException {
        var ir = codec.parse(UNKNOWN_BLOCK_BODY);

        assertThat(ir.messages().get(0).parts()).element(1)
                .isInstanceOfSatisfying(AiContentPart.Custom.class, custom -> {
                    assertThat(custom.type()).isEqualTo("input_audio");
                    assertThat(custom.raw().get("input_audio").get("format").asText()).isEqualTo("wav");
                });

        assertJsonRoundTrip(UNKNOWN_BLOCK_BODY);
    }

    @Test
    void rejectsBodyWithoutMessages() {
        assertThatThrownBy(() -> codec.parse("{\"model\":\"gpt-4o-mini\"}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("messages");
    }

    @Test
    void rejectsInvalidJson() {
        assertThatThrownBy(() -> codec.parse("{"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("无法解析");
    }

    private void assertJsonRoundTrip(String body) throws JsonProcessingException {
        var serialized = codec.serialize(codec.parse(body));
        assertThat(readTree(serialized)).isEqualTo(readTree(body));
    }

    private JsonNode readTree(String body) throws JsonProcessingException {
        return mapper.readTree(body);
    }
}
