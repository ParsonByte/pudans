package cn.tolim.pudans.core.ai.harness.journal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ThreadJournalAppendRetryTest {

    @Test
    void retriesWithFreshHeadWhenSequenceConflicts() {
        var attempts = new AtomicInteger();
        var journal = new ThreadJournal() {
            @Override
            public Mono<JournalHead> head() {
                return Mono.just(new JournalHead(attempts.get()));
            }

            @Override
            public Mono<List<JournalEvent>> append(long expectedSeq, List<JournalEventDraft> drafts) {
                if (attempts.getAndIncrement() == 0) {
                    return Mono.error(new JournalConflictException("conflict"));
                }
                var draft = drafts.get(0);
                return Mono.just(List.of(new JournalEvent(
                        draft.eventId(),
                        expectedSeq + 1,
                        draft.occurredAt(),
                        draft.type(),
                        draft.runId(),
                        draft.turnId(),
                        true,
                        "{}")));
            }

            @Override
            public Flux<JournalEvent> scan(long after, long through) {
                return Flux.empty();
            }
        };

        var events = journal
                .append(List.of(JournalEventDraft.of(JournalEventType.RUN_START, "run-1", Map.of("k", "v"))))
                .block();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).seq()).isEqualTo(2);
        assertThat(attempts.get()).isEqualTo(2);
    }
}
