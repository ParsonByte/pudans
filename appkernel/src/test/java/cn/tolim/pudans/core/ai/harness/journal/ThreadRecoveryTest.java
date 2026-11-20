package cn.tolim.pudans.core.ai.harness.journal;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateCodec;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ThreadRecoveryTest {

    @Test
    void reconcilesEveryUncertainToolCallAndRestoresCheckpoint() {
        var journal = uncertainJournal();
        var outcome = new ThreadRecovery.ToolOutcome("call-1", "echo", "echo:ok", false);

        var projection =
                ThreadRecovery.reconcile(journal, Map.of("call-1", outcome), "host timeout")
                        .block();

        assertThat(projection).isNotNull();
        assertThat(projection.uncertainToolCalls()).isEmpty();
        assertThat(projection.activeRuns()).isEmpty();
        assertThat(projection.state()).isNotNull();
        assertThat(projection.state().messages()).hasSize(2);
        assertThat(projection.state().messages().get(1))
                .isInstanceOf(ToolExecutionResultMessage.class);
        var result = (ToolExecutionResultMessage) projection.state().messages().get(1);
        assertThat(result.id()).isEqualTo("call-1");
        assertThat(result.text()).isEqualTo("echo:ok");
        assertThat(result.isError()).isFalse();
    }

    @Test
    void rejectsPartialOutcomeSetAndBlankReason() {
        var journal = uncertainJournal();
        var outcome = new ThreadRecovery.ToolOutcome("call-1", "echo", "echo:ok", false);

        StepVerifier.create(ThreadRecovery.reconcile(journal, Map.of(), "host timeout"))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(ThreadRecovery.reconcile(journal, Map.of("call-1", outcome), " "))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void abandonsUncertainToolCallsWithAnErrorResult() {
        var projection = ThreadRecovery.abandonInterrupted(uncertainJournal(), "replica-startup").block();

        assertThat(projection).isNotNull();
        assertThat(projection.uncertainToolCalls()).isEmpty();
        assertThat(projection.activeRuns()).isEmpty();
        assertThat(projection.state()).isNotNull();
        var result = (ToolExecutionResultMessage) projection.state().messages().get(1);
        assertThat(result.id()).isEqualTo("call-1");
        assertThat(result.toolName()).isEqualTo("echo");
        assertThat(result.isError()).isTrue();
    }

    @Test
    void marksInterruptedRunsWithoutABaseline() {
        var journal = new InMemoryThreadJournal();
        journal.append(JournalEventDraft.of(JournalEventType.RUN_START, "run-1", Map.of()))
                .block();

        var projection = ThreadRecovery.abandonInterrupted(journal, "replica-startup").block();

        assertThat(projection).isNotNull();
        assertThat(projection.state()).isNull();
        assertThat(projection.activeRuns()).isEmpty();
        assertThat(journal.scan(0, Long.MAX_VALUE).collectList().block())
                .extracting(JournalEvent::type)
                .contains(JournalEventType.RECOVERY_APPLIED);
    }

    @Test
    void rejectsRevisionWithoutBaseline() {
        var journal = new InMemoryThreadJournal();
        journal.append(JournalEventDraft.of(JournalEventType.RUN_START, "run-1", Map.of()))
                .block();

        StepVerifier.create(ThreadRecovery.reconcile(journal, Map.of(), "host timeout"))
                .expectError(JournalException.class)
                .verify();
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
