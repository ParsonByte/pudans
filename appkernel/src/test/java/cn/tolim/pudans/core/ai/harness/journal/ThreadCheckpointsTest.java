package cn.tolim.pudans.core.ai.harness.journal;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.agent.AgentState;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class ThreadCheckpointsTest {

    @Test
    void initializesEmptyJournalAndRejectsSecondBaseline() {
        var journal = new InMemoryThreadJournal();
        var state = new AgentState();
        state.addMessage(UserMessage.from("hello"));

        var checkpoint = ThreadCheckpoints.initialize(journal, state).block();

        assertThat(checkpoint).isNotNull();
        assertThat(checkpoint.type()).isEqualTo(JournalEventType.STATE_CHECKPOINT);
        assertThat(checkpoint.seq()).isEqualTo(1);
        var projection = JournalProjection.read(journal).block();
        assertThat(projection.state()).isNotNull();
        assertThat(projection.state().messages()).hasSize(1);

        StepVerifier.create(ThreadCheckpoints.initialize(journal, new AgentState()))
                .expectError(JournalException.class)
                .verify();
    }

    @Test
    void appendsCheckpointAtHead() {
        var journal = new InMemoryThreadJournal();
        var initial = new AgentState();
        initial.addMessage(UserMessage.from("hello"));
        ThreadCheckpoints.initialize(journal, initial).block();
        var updated = new AgentState();
        updated.addMessage(UserMessage.from("hello"));
        updated.addMessage(UserMessage.from("next"));

        var checkpoint = ThreadCheckpoints.checkpoint(journal, updated, "turn-end").block();

        assertThat(checkpoint.seq()).isEqualTo(2);
        var projection = JournalProjection.read(journal).block();
        assertThat(projection.state()).isNotNull();
        assertThat(projection.state().messages()).hasSize(2);
    }

    @Test
    void rejectsBlankCheckpointReason() {
        var journal = new InMemoryThreadJournal();

        StepVerifier.create(ThreadCheckpoints.checkpoint(journal, new AgentState(), " "))
                .expectError(IllegalArgumentException.class)
                .verify();
    }
}