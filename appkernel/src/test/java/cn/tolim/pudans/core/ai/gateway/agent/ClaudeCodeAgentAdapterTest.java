package cn.tolim.pudans.core.ai.gateway.agent;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.contract.ai.AiProvider;
import cn.tolim.pudans.infra.api.utils.JsonUtils;
import cn.tolim.pudans.core.ai.gateway.AiChatRequest;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatIr;
import cn.tolim.pudans.core.ai.gateway.ir.AiChatMessage;
import cn.tolim.pudans.core.ai.gateway.ir.AiContentPart;
import cn.tolim.pudans.core.ai.gateway.ir.AiRequestParams;
import cn.tolim.pudans.core.ai.gateway.ir.AiTool;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaudeCodeAgentAdapterTest {

    private final ObjectMapper mapper = JsonUtils.mapper();

    private final ClaudeCodeAgentAdapter adapter = new ClaudeCodeAgentAdapter();

    @Test
    void extractsLastUserTextSkippingSystemRemindersAndToolResults() {
        var ir = ir(
                List.of(user(
                        new AiContentPart.Text("<system-reminder>workspace</system-reminder>", null),
                        new AiContentPart.Text("Fix the failing test", null),
                        new AiContentPart.Text("<system-reminder>late</system-reminder>", null),
                        new AiContentPart.ToolResult(
                                "toolu_1", mapper.getNodeFactory().textNode("ok"), false, null))),
                List.of(),
                Map.of());

        assertThat(adapter.extractUserText(request(ir))).contains("Fix the failing test");
    }

    @Test
    void returnsEmptyWhenLastUserMessageHasNoUserText() {
        var ir = ir(
                List.of(
                        user(new AiContentPart.Text("earlier typed text", null)),
                        assistant(new AiContentPart.Text("working", null)),
                        user(
                                new AiContentPart.ToolResult(
                                        "toolu_1", mapper.getNodeFactory().textNode("ok"), false, null),
                                new AiContentPart.Text("<system-reminder>env</system-reminder>", null))),
                List.of(),
                Map.of());

        assertThat(adapter.extractUserText(request(ir))).isEmpty();
    }

    @Test
    void classifiesForkWhenCacheMarkerSitsOnSecondToLastMessage() throws JsonProcessingException {
        var ir = ir(
                List.of(
                        user(new AiContentPart.Text("first", cacheControl())),
                        user(new AiContentPart.Text("second", null))),
                List.of(),
                Map.of());

        assertThat(adapter.classifyRequest(request(ir))).isEqualTo(AiRequestKind.FORK);
    }

    @Test
    void classifiesMainWhenCacheMarkerSitsOnLastMessage() throws JsonProcessingException {
        var ir = ir(
                List.of(
                        user(new AiContentPart.Text("first", null)),
                        assistant(new AiContentPart.Text("second", cacheControl()))),
                List.of(),
                Map.of());

        assertThat(adapter.classifyRequest(request(ir))).isEqualTo(AiRequestKind.MAIN);
    }

    @Test
    void classifiesSideQueryWhenToolsAreEmptyAndThinkingIsDisabled() throws JsonProcessingException {
        var ir = ir(
                List.of(user(new AiContentPart.Text("Generate a title", null))),
                List.of(),
                Map.of("thinking", disabledThinking()));

        assertThat(adapter.classifyRequest(request(ir))).isEqualTo(AiRequestKind.SIDEQUERY);
    }

    @Test
    void defaultsToMainWithoutSideQuerySignals() throws JsonProcessingException {
        var withoutSignals = ir(
                List.of(user(new AiContentPart.Text("hello", null))),
                List.of(),
                Map.of());
        assertThat(adapter.classifyRequest(request(withoutSignals))).isEqualTo(AiRequestKind.MAIN);

        var withTools = ir(
                List.of(user(new AiContentPart.Text("hello", null))),
                List.of(new AiTool("get_weather", null, null, null)),
                Map.of("thinking", disabledThinking()));
        assertThat(adapter.classifyRequest(request(withTools))).isEqualTo(AiRequestKind.MAIN);
    }

    @Test
    void exposesClaudeCodeSource() {
        assertThat(adapter.source()).isEqualTo("claude-code");
    }

    private JsonNode cacheControl() throws JsonProcessingException {
        return mapper.readTree("{\"type\":\"ephemeral\"}");
    }

    private JsonNode disabledThinking() throws JsonProcessingException {
        return mapper.readTree("{\"type\":\"disabled\"}");
    }

    private static AiChatIr ir(List<AiChatMessage> messages, List<AiTool> tools, Map<String, JsonNode> extra) {
        var params = new AiRequestParams(
                "claude-sonnet-4-5", null, 512, null, null, List.of(), null, extra);
        return new AiChatIr(messages, tools, params, false);
    }

    private static AiChatRequest request(AiChatIr ir) {
        return new AiChatRequest(AiProvider.Protocol.ANTHROPIC, ir);
    }

    private static AiChatMessage user(AiContentPart... parts) {
        return new AiChatMessage("user", List.of(parts));
    }

    private static AiChatMessage assistant(AiContentPart... parts) {
        return new AiChatMessage("assistant", List.of(parts));
    }
}
