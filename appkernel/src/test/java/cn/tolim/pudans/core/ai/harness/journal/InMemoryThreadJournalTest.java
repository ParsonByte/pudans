package cn.tolim.pudans.core.ai.harness.journal;

import static org.assertj.core.api.Assertions.assertThat;

import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

class InMemoryThreadJournalTest {

    @Test
    void appendsAndReplaysInSequence() {
        var journal = new InMemoryThreadJournal();

        var first = journal.append(draft(JournalEventType.RUN_START, "run-1")).block();
        var second = journal.append(draft(JournalEventType.RUN_END, "run-1")).block();

        assertThat(first).isNotNull();
        assertThat(first.seq()).isEqualTo(1);
        assertThat(second).isNotNull();
        assertThat(second.seq()).isEqualTo(2);
        assertThat(journal.head().block().seq()).isEqualTo(2);

        StepVerifier.create(journal.scan(0, 2))
                .assertNext(event -> assertThat(event.type()).isEqualTo(JournalEventType.RUN_START))
                .assertNext(event -> assertThat(event.type()).isEqualTo(JournalEventType.RUN_END))
                .verifyComplete();
        StepVerifier.create(journal.scan(1, 2))
                .assertNext(event -> assertThat(event.type()).isEqualTo(JournalEventType.RUN_END))
                .verifyComplete();
    }

    @Test
    void rejectsStaleExpectedSequence() {
        var journal = new InMemoryThreadJournal();
        journal.append(draft(JournalEventType.RUN_START, "run-1")).block();

        StepVerifier.create(journal.append(0, List.of(draft(JournalEventType.RUN_END, "run-1"))))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(JournalException.class)
                        .hasMessageContaining("序号冲突"))
                .verify();

        assertThat(journal.head().block().seq()).isEqualTo(1);
    }

    @Test
    void sharesJournalByRuntimeContext() {
        var store = new InMemoryThreadJournalStore();
        var first = store.open(RuntimeContext.of("user-1", "session-1")).block();
        var second = store.open(RuntimeContext.of("user-1", "session-1")).block();

        first.append(draft(JournalEventType.RUN_START, "run-1")).block();

        assertThat(second.head().block().seq()).isEqualTo(1);
        assertThat(store.open(RuntimeContext.of("user-1", "session-2"))
                        .block()
                        .head()
                        .block()
                        .seq())
                .isZero();
    }

    private static JournalEventDraft draft(JournalEventType type, String runId) {
        return JournalEventDraft.of(type, runId, Map.of("runId", runId));
    }
}