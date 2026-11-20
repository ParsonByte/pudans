package cn.tolim.pudans.core.ai.protocol.agui;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.event.AgentEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AguiEventConverterTest {

    private static final String THREAD_ID = "thread-1";

    private static final String RUN_ID = "run-1";

    private final AguiEventConverter converter = new AguiEventConverter(THREAD_ID, RUN_ID);

    @Test
    void mapsRunAndTextLifecycle() {
        assertThat(converter.convert(new AgentEvent.AgentStart(RUN_ID, "agent-a")))
                .containsExactly(new AguiEvent.RunStarted(THREAD_ID, RUN_ID));

        var first = converter.convert(new AgentEvent.TextBlockDelta(RUN_ID, "你"));
        var messageId = messageId(first.get(0));
        assertThat(first).containsExactly(
                new AguiEvent.TextMessageStart(THREAD_ID, RUN_ID, messageId, "assistant"),
                new AguiEvent.TextMessageContent(THREAD_ID, RUN_ID, messageId, "你"));

        assertThat(converter.convert(new AgentEvent.TextBlockDelta(RUN_ID, "好")))
                .containsExactly(new AguiEvent.TextMessageContent(THREAD_ID, RUN_ID, messageId, "好"));
        assertThat(converter.convert(new AgentEvent.TextBlockEnd(RUN_ID, "你好")))
                .containsExactly(new AguiEvent.TextMessageEnd(THREAD_ID, RUN_ID, messageId));
        assertThat(converter.convert(new AgentEvent.AgentResult(RUN_ID, "你好")))
                .containsExactly(new AguiEvent.RunFinished(THREAD_ID, RUN_ID, "你好"));
        assertThat(converter.convert(new AgentEvent.AgentEnd(RUN_ID, "agent-a"))).isEmpty();
    }

    @Test
    void mapsModelStepsAndHarnessSpecificEvents() {
        assertThat(converter.convert(new AgentEvent.ModelCallStart(RUN_ID, 0)))
                .containsExactly(new AguiEvent.StepStarted(THREAD_ID, RUN_ID, "model:0"));
        assertThat(converter.convert(new AgentEvent.ModelCallEnd(RUN_ID, 0, "STOP")))
                .containsExactly(new AguiEvent.StepFinished(THREAD_ID, RUN_ID, "model:0"));
        assertThat(converter.convert(new AgentEvent.HintBlock(RUN_ID, "已压缩")))
                .containsExactly(new AguiEvent.Custom(THREAD_ID, RUN_ID, "hint", "已压缩"));
        assertThat(converter.convert(new AgentEvent.RequireUserConfirm(
                        RUN_ID, "call-1", "echo", "需要确认")))
                .containsExactly(new AguiEvent.Custom(
                        THREAD_ID, RUN_ID, "require_user_confirm", "需要确认"));
        assertThat(converter.convert(new AgentEvent.SubagentExposed(RUN_ID, "helper")))
                .containsExactly(new AguiEvent.Custom(
                        THREAD_ID, RUN_ID, "subagent_exposed", "helper"));
    }

    @Test
    void mapsThinkingDeltasToTheirOwnTextMessage() {
        var first = converter.convert(new AgentEvent.ThinkingBlockDelta(RUN_ID, "思考"));
        var messageId = messageId(first.get(0));
        assertThat(first).containsExactly(
                new AguiEvent.TextMessageStart(THREAD_ID, RUN_ID, messageId, "assistant"),
                new AguiEvent.TextMessageContent(THREAD_ID, RUN_ID, messageId, "思考"));
        assertThat(converter.convert(new AgentEvent.ThinkingBlockEnd(RUN_ID, "思考")))
                .containsExactly(new AguiEvent.TextMessageEnd(THREAD_ID, RUN_ID, messageId));
    }

    @Test
    void closesTextMessageFromEndTextWhenNoDeltaArrived() {
        var events = converter.convert(new AgentEvent.TextBlockEnd(RUN_ID, "完整文本"));
        var messageId = messageId(events.get(0));
        assertThat(events).containsExactly(
                new AguiEvent.TextMessageStart(THREAD_ID, RUN_ID, messageId, "assistant"),
                new AguiEvent.TextMessageContent(THREAD_ID, RUN_ID, messageId, "完整文本"),
                new AguiEvent.TextMessageEnd(THREAD_ID, RUN_ID, messageId));
    }

    @Test
    void mapsToolCallLifecycleAndResult() {
        assertThat(converter.convert(
                        new AgentEvent.ToolCallStart(RUN_ID, "call-1", "search", "{\"q\":\"pudans\"}")))
                .containsExactly(new AguiEvent.ToolCallStart(THREAD_ID, RUN_ID, "call-1", "search"));
        assertThat(converter.convert(
                        new AgentEvent.ToolCallEnd(RUN_ID, "call-1", "search", "{\"q\":\"pudans\"}")))
                .containsExactly(
                        new AguiEvent.ToolCallArgs(THREAD_ID, RUN_ID, "call-1", "{\"q\":\"pudans\"}"),
                        new AguiEvent.ToolCallEnd(THREAD_ID, RUN_ID, "call-1"));
        var result = (AguiEvent.ToolCallResult) converter
                .convert(new AgentEvent.ToolResultEnd(RUN_ID, "call-1", "search", "结果", false))
                .get(0);
        assertThat(result.toolCallId()).isEqualTo("call-1");
        assertThat(result.content()).isEqualTo("结果");
        assertThat(result.role()).isEqualTo("tool");
        assertThat(result.messageId()).startsWith("msg-");
    }

    @Test
    void doesNotRepeatArgumentsStreamedAsDeltas() {
        converter.convert(new AgentEvent.ToolCallStart(RUN_ID, "call-1", "search", "{}"));
        assertThat(converter.convert(new AgentEvent.ToolCallDelta(RUN_ID, "call-1", "{\"q\":")))
                .containsExactly(new AguiEvent.ToolCallArgs(THREAD_ID, RUN_ID, "call-1", "{\"q\":"));
        assertThat(converter.convert(new AgentEvent.ToolCallEnd(RUN_ID, "call-1", "search", "{\"q\":\"x\"}")))
                .containsExactly(new AguiEvent.ToolCallEnd(THREAD_ID, RUN_ID, "call-1"));
    }

    @Test
    void closesOpenTextMessageWhenAgentEndArrives() {
        converter.convert(new AgentEvent.TextBlockDelta(RUN_ID, "片段"));
        var events = converter.convert(new AgentEvent.AgentEnd(RUN_ID, "agent-a"));
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageEnd.class);
        assertThat(events.get(1)).isEqualTo(new AguiEvent.RunFinished(THREAD_ID, RUN_ID, null));
    }

    @Test
    void emitsRunErrorAndStopsFurtherMapping() {
        converter.convert(new AgentEvent.TextBlockDelta(RUN_ID, "片段"));
        var events = converter.fail("模型调用失败。", "ai-agui-run-failed");
        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOf(AguiEvent.TextMessageEnd.class);
        assertThat(events.get(1)).isEqualTo(new AguiEvent.RunError(
                THREAD_ID, RUN_ID, "模型调用失败。", "ai-agui-run-failed"));
        assertThat(converter.finish("你好")).isEmpty();
        assertThat(converter.convert(new AgentEvent.AgentEnd(RUN_ID, "agent-a"))).isEmpty();
    }

    @Test
    void serializesEventTypeAndOmitsNullFields() throws Exception {
        var mapper = new ObjectMapper();

        var started = mapper.writeValueAsString(new AguiEvent.RunStarted(THREAD_ID, RUN_ID));
        assertThat(started)
                .contains("\"type\":\"RUN_STARTED\"")
                .contains("\"threadId\":\"thread-1\"")
                .contains("\"runId\":\"run-1\"");

        var finished = mapper.writeValueAsString(new AguiEvent.RunFinished(THREAD_ID, RUN_ID, null));
        assertThat(finished).contains("\"type\":\"RUN_FINISHED\"").doesNotContain("result");
    }

    private static String messageId(AguiEvent event) {
        return ((AguiEvent.TextMessageStart) event).messageId();
    }
}