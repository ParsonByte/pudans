package cn.tolim.pudans.core.ai.harness.journal;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import cn.tolim.pudans.core.ai.harness.agent.AgentStateCodec;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JournalProjectionTest {

    @Test
    void projectsCheckpointRunAndPendingToolState() {
        var journal = new InMemoryThreadJournal();
        var state = new AgentState();
        state.addMessage(UserMessage.from("hello"));

        journal.append(JournalEventDraft.of(JournalEventType.RUN_START, "run-1", Map.of()))
                .block();
        journal.append(JournalEventDraft.of(
                        JournalEventType.TOOL_CALL_START,
                        "run-1",
                        Map.of("toolCallId", "call-1", "toolName", "echo")))
                .block();
        appendCheckpoint(journal, "run-1", state);

        var projection = JournalProjection.read(journal).block();

        assertThat(projection).isNotNull();
        assertThat(projection.asOfSeq()).isEqualTo(3);
        assertThat(projection.state()).isNotNull();
        assertThat(projection.state().messages()).hasSize(1);
        assertThat(projection.activeRuns()).containsExactly("run-1");
        assertThat(projection.uncertainToolCalls()).containsExactly("call-1");
        assertThat(projection.toolNames()).containsEntry("call-1", "echo");
        assertThat(projection.checkpointRunId()).isEqualTo("run-1");
    }

    @Test
    void clearsPendingFactsWhenResultsAndRunsEnd() {
        var journal = new InMemoryThreadJournal();
        var state = new AgentState();
        state.addMessage(UserMessage.from("hello"));
        journal.append(JournalEventDraft.of(JournalEventType.RUN_START, "run-1", Map.of()))
                .block();
        journal.append(JournalEventDraft.of(
                        JournalEventType.TOOL_CALL_START,
                        "run-1",
                        Map.of("toolCallId", "call-1")))
                .block();
        appendCheckpoint(journal, "run-1", state);
        journal.append(JournalEventDraft.of(
                        JournalEventType.TOOL_RESULT_END,
                        "run-1",
                        Map.of("toolCallId", "call-1", "error", false)))
                .block();
        journal.append(JournalEventDraft.of(JournalEventType.RUN_END, "run-1", Map.of()))
                .block();

        var projection = JournalProjection.read(journal).block();

        assertThat(projection.uncertainToolCalls()).isEmpty();
        assertThat(projection.activeRuns()).isEmpty();
    }

    @Test
    void appliesRecoveryFactsAndUsesLatestCheckpoint() {
        var journal = new InMemoryThreadJournal();
        var first = new AgentState();
        first.addMessage(UserMessage.from("first"));
        appendCheckpoint(journal, "run-1", first);
        journal.append(JournalEventDraft.of(
                        JournalEventType.RECOVERY_APPLIED,
                        "run-1",
                        Map.of(
                                "resolvedToolCallIds", List.of("call-1"),
                                "interruptedRuns", List.of("run-1"))))
                .block();
        var second = new AgentState();
        second.addMessage(UserMessage.from("second"));
        second.addMessage(UserMessage.from("third"));
        appendCheckpoint(journal, "run-2", second);

        var projection = JournalProjection.read(journal).block();

        assertThat(projection.state()).isNotNull();
        assertThat(projection.state().messages()).hasSize(2);
        assertThat(projection.checkpointRunId()).isEqualTo("run-2");
        assertThat(projection.uncertainToolCalls()).isEmpty();
        assertThat(projection.activeRuns()).isEmpty();
    }

    @Test
    void reportsNoStateWithoutCheckpoint() {
        var journal = new InMemoryThreadJournal();
        journal.append(JournalEventDraft.of(JournalEventType.RUN_START, "run-1", Map.of()))
                .block();

        var projection = JournalProjection.read(journal).block();

        assertThat(projection.state()).isNull();
        assertThat(projection.asOfSeq()).isEqualTo(1);
    }

    private static void appendCheckpoint(
            ThreadJournal journal, String runId, AgentState state) {
        journal.append(JournalEventDraft.of(
                        JournalEventType.STATE_CHECKPOINT,
                        runId,
                        Map.of("stateJson", AgentStateCodec.encode(state), "reason", "test")))
                .block();
    }
}
