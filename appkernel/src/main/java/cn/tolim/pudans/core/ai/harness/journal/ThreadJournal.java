package cn.tolim.pudans.core.ai.harness.journal;

import java.time.Instant;
import java.util.List;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ThreadJournal {

    Mono<JournalHead> head();

    Mono<List<JournalEvent>> append(long expectedSeq, List<JournalEventDraft> drafts);

    default Mono<JournalEvent> append(JournalEventDraft draft) {
        return append(List.of(draft)).map(events -> events.get(0));
    }

    default Mono<List<JournalEvent>> append(List<JournalEventDraft> drafts) {
        return head().flatMap(head -> append(head.seq(), drafts));
    }

    default Mono<Instant> lastOccurredAt() {
        return head().flatMap(head -> head.seq() == 0
                ? Mono.just(Instant.EPOCH)
                : scan(head.seq() - 1, head.seq()).next().map(JournalEvent::occurredAt));
    }

    Flux<JournalEvent> scan(long after, long through);
}
