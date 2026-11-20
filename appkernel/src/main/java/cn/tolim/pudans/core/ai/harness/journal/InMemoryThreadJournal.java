package cn.tolim.pudans.core.ai.harness.journal;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class InMemoryThreadJournal implements ThreadJournal {

    private final List<JournalEvent> events = new ArrayList<>();

    private final Clock clock;

    public InMemoryThreadJournal() {
        this(Clock.systemUTC());
    }

    InMemoryThreadJournal(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }

    @Override
    public Mono<JournalHead> head() {
        synchronized (events) {
            return Mono.just(new JournalHead(lastSeq()));
        }
    }

    @Override
    public Mono<List<JournalEvent>> append(long expectedSeq, List<JournalEventDraft> drafts) {
        if (expectedSeq < 0) {
            return Mono.error(new JournalException("线程日志期望序号不能为负数。"));
        }
        if (drafts == null || drafts.isEmpty()) {
            return Mono.just(List.of());
        }
        synchronized (events) {
            var current = lastSeq();
            if (current != expectedSeq) {
                return Mono.error(new JournalException(
                        "线程日志追加序号冲突：期望 " + expectedSeq + "，实际 " + current + "。"));
            }
            var appended = new ArrayList<JournalEvent>(drafts.size());
            for (var draft : drafts) {
                current++;
                var event = new JournalEvent(
                        draft.eventId(),
                        current,
                        draft.occurredAt(),
                        draft.type(),
                        draft.runId(),
                        draft.turnId(),
                        draft.required(),
                        draft.payloadJson());
                events.add(event);
                appended.add(event);
            }
            return Mono.just(List.copyOf(appended));
        }
    }

    @Override
    public Flux<JournalEvent> scan(long after, long through) {
        if (after < 0 || through < after) {
            return Flux.error(new IllegalArgumentException("线程日志扫描区间无效。"));
        }
        synchronized (events) {
            return Flux.fromIterable(events.stream()
                    .filter(event -> event.seq() > after && event.seq() <= through)
                    .toList());
        }
    }

    private long lastSeq() {
        return events.isEmpty() ? 0 : events.get(events.size() - 1).seq();
    }
}