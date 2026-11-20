package cn.tolim.pudans.core.ai.harness.journal;

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

    Flux<JournalEvent> scan(long after, long through);
}