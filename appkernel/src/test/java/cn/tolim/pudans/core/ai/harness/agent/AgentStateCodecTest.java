package cn.tolim.pudans.core.ai.harness.agent;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.plan.TodoItem;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentStateCodecTest {

    @Test
    void roundTripsMessagesPlanTodosAndAttributes() {
        var state = new AgentState();
        state.addMessage(UserMessage.from("hello"));
        state.addMessage(AiMessage.builder()
                .text("working")
                .thinking("reasoning")
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("echo")
                        .arguments("{\"text\":\"hello\"}")
                        .build()))
                .build());
        state.addMessage(ToolExecutionResultMessage.builder()
                .id("call-1")
                .toolName("echo")
                .text("echo:hello")
                .isError(false)
                .build());
        state.setSummary("summary");
        state.setAttribute("k", "v");
        state.planMode().enter();
        state.planMode().write("plan");
        state.setTodos(List.of(new TodoItem("1", "task", "pending")));

        var decoded = AgentStateCodec.decode(AgentStateCodec.encode(state));

        assertThat(decoded.summary()).isEqualTo("summary");
        assertThat(decoded.attributes()).containsEntry("k", "v");
        assertThat(decoded.planMode().active()).isTrue();
        assertThat(decoded.planMode().planContent()).isEqualTo("plan");
        assertThat(decoded.todos()).containsExactly(new TodoItem("1", "task", "pending"));
        assertThat(decoded.messages()).hasSize(3);
        assertThat(decoded.messages().get(1)).isInstanceOf(AiMessage.class);
        assertThat(((AiMessage) decoded.messages().get(1)).thinking()).isEqualTo("reasoning");
        assertThat(decoded.messages().get(2)).isInstanceOf(ToolExecutionResultMessage.class);
    }

    @Test
    void decodesAnEmptyPayload() {
        var state = AgentStateCodec.decode("{}");

        assertThat(state.messages()).isEmpty();
        assertThat(state.planMode().active()).isFalse();
        assertThat(state.todos()).isEmpty();
        assertThat(state.attributes()).isEmpty();
    }
}