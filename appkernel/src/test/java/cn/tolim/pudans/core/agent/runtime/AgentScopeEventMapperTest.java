package cn.tolim.pudans.core.agent.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.AgentStartEvent;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.message.GenerateReason;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.message.UserMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentScopeEventMapperTest {

    @Test
    void mapsStartsAndTextDeltasWithoutExposingTheSdkType() {
        var start = AgentScopeEventMapper.map(
                new AgentStartEvent("session-1", "reply-1", "pudans-agent"));
        var delta = AgentScopeEventMapper.map(
                new TextBlockDeltaEvent("reply-1", "block-1", "hello"));

        assertThat(start.type()).isEqualTo(AgentRunEvents.RUN_STARTED);
        assertThat(start.data())
                .containsEntry("sessionId", "session-1")
                .containsEntry("agentName", "pudans-agent");
        assertThat(delta.type()).isEqualTo(AgentRunEvents.MESSAGE_DELTA);
        assertThat(delta.data())
                .containsEntry("replyId", "reply-1")
                .containsEntry("blockId", "block-1")
                .containsEntry("delta", "hello");
    }

    @Test
    void mapsAResultToTheCompletedTerminalEvent() {
        var result = new UserMessage("done").withGenerateReason(GenerateReason.MODEL_STOP);
        var mapped = AgentScopeEventMapper.map(new AgentResultEvent(result));

        assertThat(mapped.type()).isEqualTo(AgentRunEvents.RUN_COMPLETED);
        assertThat(mapped.data()).containsEntry("text", "done");
        assertThat(AgentScopeEventMapper.assistantContent(result))
                .containsEntry("type", "text")
                .containsEntry("text", "done");
    }

    @Test
    void mapsToolConfirmationToTheApprovalEventAndKeepsTheToolCall() {
        var toolCall = new ToolUseBlock(
                "call-1", "generate_media", Map.of("prompt", "a real prompt"));
        var mapped = AgentScopeEventMapper.map(
                new RequireUserConfirmEvent("reply-1", List.of(toolCall)));

        assertThat(mapped.type()).isEqualTo(AgentRunEvents.TOOL_APPROVAL_REQUIRED);
        assertThat(mapped.pendingConfirmations()).containsExactly(toolCall);
        assertThat(mapped.data())
                .containsEntry("replyId", "reply-1")
                .containsKey("toolCalls");
    }
}
