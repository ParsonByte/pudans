package cn.tolim.pudans.core.ai.harness.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateCodec;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@ExtendWith(MockitoExtension.class)
class JournalRecoveryTest {

    @Mock
    ThreadJournalStore journalStore;

    @Mock
    AgentStateStore stateStore;

    @Test
    void recoversStaleThreadsAndPersistsTheCheckpoint() {
        var context = RuntimeContext.of("user-1", "session-1");
        var journal = uncertainJournal();
        when(journalStore.staleActiveThreads(any())).thenReturn(Flux.just(context));
        when(journalStore.open(context)).thenReturn(Mono.just(journal));
        when(stateStore.save(eq("user-1"), eq("session-1"), any())).thenReturn(Mono.empty());
        var service = new JournalRecovery(journalStore, stateStore);

        StepVerifier.create(service.recoverStale()).verifyComplete();

        var captor = ArgumentCaptor.forClass(AgentState.class);
        verify(stateStore).save(eq("user-1"), eq("session-1"), captor.capture());
        assertThat(captor.getValue().messages()).hasSize(2);
        var result = (ToolExecutionResultMessage) captor.getValue().messages().get(1);
        assertThat(result.id()).isEqualTo("call-1");
        assertThat(result.isError()).isTrue();
    }

    private static InMemoryThreadJournal uncertainJournal() {
        var journal = new InMemoryThreadJournal();
        var state = new AgentState();
        state.addMessage(UserMessage.from("run"));
        journal.append(JournalEventDraft.of(JournalEventType.RUN_START, "run-1", Map.of()))
                .block();
        journal.append(JournalEventDraft.of(
                        JournalEventType.TOOL_CALL_START,
                        "run-1",
                        Map.of("toolCallId", "call-1", "toolName", "echo")))
                .block();
        journal.append(JournalEventDraft.of(
                        JournalEventType.STATE_CHECKPOINT,
                        "run-1",
                        Map.of("stateJson", AgentStateCodec.encode(state), "reason", "test")))
                .block();
        return journal;
    }
}
