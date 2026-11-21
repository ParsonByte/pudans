package cn.tolim.pudans.core.ai.gateway.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AnthropicMessagesCodecTest {

    private static final String TEXT_AND_TOOL_CALL_BODY = """
            {
              "model": "claude-sonnet-4-5",
              "system": [
                {"type": "text", "text": "You are helpful.", "cache_control": {"type": "ephemeral"}}
              ],
              "messages": [
                {"role": "user", "content": "What is the weather in Paris?"},
                {"role": "assistant", "content": [
                  {"type": "thinking", "thinking": "I should check the weather.", "signature": "sig_1"},
                  {"type": "text", "text": "Let me check."},
                  {"type": "tool_use", "id": "toolu_1", "name": "get_weather", "input": {"city": "Paris"}}
                ]},
                {"role": "user", "content": [
                  {"type": "tool_result", "tool_use_id": "toolu_1", "content": "sunny, 20C",
                   "cache_control": {"type": "ephemeral"}},
                  {"type": "text", "text": "Thanks"}
                ]}
              ],
              "tools": [
                {"name": "get_weather", "description": "Get the current weather",
                 "input_schema": {"type": "object", "properties": {"city": {"type": "string"}}}}
              ],
              "max_tokens": 512,
              "temperature": 0.1,
              "thinking": {"type": "disabled"},
              "stream": false
            }
            """;

    private static final String UNKNOWN_BLOCK_BODY = """
            {
              "model": "claude-sonnet-4-5",
              "max_tokens": 64,
              "messages": [
                {"role": "assistant", "content": [
                  {"type": "redacted_thinking", "data": "opaque"},
                  {"type": "text", "text": "done"}
                ]}
              ],
              "stream": false
            }
            """;

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final AnthropicMessagesCodec codec = new AnthropicMessagesCodec(mapper);

    @Test
    void roundTripsTextAndToolCalls() throws JsonProcessingException {
        var ir = codec.parse(TEXT_AND_TOOL_CALL_BODY);

        assertThat(codec.protocol()).isEqualTo(AiProvider.Protocol.ANTHROPIC);
        assertThat(ir.stream()).isFalse();
        assertThat(ir.requestParams().model()).isEqualTo("claude-sonnet-4-5");
        assertThat(ir.requestParams().maxTokens()).isEqualTo(512);
        assertThat(ir.requestParams().extra()).containsKey("thinking");
        assertThat(ir.tools()).singleElement().satisfies(tool ->
                assertThat(tool.parameters().get("type").asText()).isEqualTo("object"));
        assertThat(ir.messages()).extracting(AiChatMessage::role)
                .containsExactly("system", "user", "assistant", "user");
        assertThat(ir.messages().get(0).parts()).singleElement()
                .isInstanceOfSatisfying(AiContentPart.Text.class, text ->
                        assertThat(text.cacheControl().get("type").asText()).isEqualTo("ephemeral"));
        assertThat(ir.messages().get(2).parts())
                .filteredOn(AiContentPart.Thinking.class::isInstance)
                .singleElement()
                .isInstanceOfSatisfying(AiContentPart.Thinking.class, thinking ->
                        assertThat(thinking.signature()).isEqualTo("sig_1"));
        assertThat(ir.messages().get(2).parts())
                .filteredOn(AiContentPart.ToolCall.class::isInstance)
                .singleElement()
                .satisfies(part -> {
                    var call = (AiContentPart.ToolCall) part;
                    assertThat(call.id()).isEqualTo("toolu_1");
                    assertThat(call.arguments().get("city").asText()).isEqualTo("Paris");
                });
        assertThat(ir.messages().get(3).parts()).element(0)
                .isInstanceOfSatisfying(AiContentPart.ToolResult.class, result -> {
                    assertThat(result.toolCallId()).isEqualTo("toolu_1");
                    assertThat(result.content().asText()).isEqualTo("sunny, 20C");
                    assertThat(result.cacheControl().get("type").asText()).isEqualTo("ephemeral");
                });

        assertJsonRoundTrip(TEXT_AND_TOOL_CALL_BODY);
    }

    @Test
    void preservesUnknownContentBlocks() throws JsonProcessingException {
        var ir = codec.parse(UNKNOWN_BLOCK_BODY);

        assertThat(ir.messages().get(0).parts()).element(0)
                .isInstanceOfSatisfying(AiContentPart.Custom.class, custom -> {
                    assertThat(custom.type()).isEqualTo("redacted_thinking");
                    assertThat(custom.raw().get("data").asText()).isEqualTo("opaque");
                });

        assertJsonRoundTrip(UNKNOWN_BLOCK_BODY);
    }

    @Test
    void rejectsBodyWithoutMessages() {
        assertThatThrownBy(() -> codec.parse("{\"model\":\"claude-sonnet-4-5\",\"max_tokens\":64}"))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("messages");
    }

    @Test
    void rejectsInvalidMessageContent() {
        var body = "{\"messages\":[{\"role\":\"user\",\"content\":42}]}";
        assertThatThrownBy(() -> codec.parse(body))
                .isInstanceOf(AiProtocolException.class)
                .hasMessageContaining("content");
    }

    private void assertJsonRoundTrip(String body) throws JsonProcessingException {
        var serialized = codec.serialize(codec.parse(body));
        assertThat(readTree(serialized)).isEqualTo(readTree(body));
    }

    private JsonNode readTree(String body) throws JsonProcessingException {
        return mapper.readTree(body);
    }
}
