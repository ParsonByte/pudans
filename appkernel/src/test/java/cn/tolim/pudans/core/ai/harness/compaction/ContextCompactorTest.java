package cn.tolim.pudans.core.ai.harness.compaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextCompactorTest {

    @Mock
    ChatModel chatModel;

    @Test
    void compactsOldMessagesAndKeepsTheTail() {
        var state = new AgentState();
        state.addMessage(UserMessage.from("one"));
        state.addMessage(AiMessage.from("two"));
        state.addMessage(UserMessage.from("three"));
        state.addMessage(AiMessage.from("four"));
        state.addMessage(UserMessage.from("five"));
        state.addMessage(AiMessage.from("six"));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("compressed facts"))
                        .build());
        var compactor = new ContextCompactor(chatModel, new CompactionConfig(4, 2));

        assertThat(compactor.compactIfNeeded(state).block()).isTrue();

        assertThat(state.messages()).hasSize(2);
        assertThat(state.summary()).contains("compressed facts");
    }

    @Test
    void compactsOnDemandBelowTheTrigger() {
        var state = new AgentState();
        state.addMessage(UserMessage.from("one"));
        state.addMessage(AiMessage.from("two"));
        state.addMessage(UserMessage.from("three"));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("forced summary"))
                        .build());
        var compactor = new ContextCompactor(chatModel, new CompactionConfig(10, 1));

        assertThat(compactor.compactNow(state).block()).isTrue();

        assertThat(state.messages()).hasSize(1);
        assertThat(state.summary()).contains("forced summary");
    }

    @Test
    void keepsMessagesWhenCompactionFails() {
        var state = new AgentState();
        state.addMessage(UserMessage.from("one"));
        state.addMessage(AiMessage.from("two"));
        state.addMessage(UserMessage.from("three"));
        when(chatModel.chat(any(ChatRequest.class)))
                .thenThrow(new IllegalStateException("summary failed"));
        var compactor = new ContextCompactor(chatModel, new CompactionConfig(1, 1));

        assertThat(compactor.compactIfNeeded(state).block()).isFalse();

        assertThat(state.messages()).hasSize(3);
        assertThat(state.summary()).isEmpty();
    }

    @Test
    void leavesShortConversationsUntouched() {
        var state = new AgentState();
        state.addMessage(UserMessage.from("one"));
        var compactor = new ContextCompactor(chatModel, new CompactionConfig(4, 2));

        assertThat(compactor.compactIfNeeded(state).block()).isFalse();
        assertThat(state.messages()).hasSize(1);
    }
}
