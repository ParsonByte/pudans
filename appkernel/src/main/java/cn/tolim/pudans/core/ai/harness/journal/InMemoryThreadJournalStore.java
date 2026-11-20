package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class InMemoryThreadJournalStore implements ThreadJournalStore {

    private final ConcurrentMap<String, InMemoryThreadJournal> journals = new ConcurrentHashMap<>();

    @Override
    public Mono<ThreadJournal> open(RuntimeContext context) {
        Objects.requireNonNull(context, "运行时上下文不能为空");
        return Mono.<ThreadJournal>fromSupplier(
                () -> journals.computeIfAbsent(context.key(), ignored -> new InMemoryThreadJournal()));
    }

    @Override
    public Flux<RuntimeContext> staleActiveThreads(Duration staleAfter) {
        Objects.requireNonNull(staleAfter, "过期时长不能为空");
        var before = Instant.now().minus(staleAfter);
        return Flux.fromIterable(journals.entrySet())
                .concatMap(entry -> JournalProjection.read(entry.getValue())
                        .filter(projection -> !projection.activeRuns().isEmpty())
                        .flatMap(projection -> entry.getValue()
                                .lastOccurredAt()
                                .filter(time -> time.isBefore(before))
                                .map(time -> context(entry.getKey())))
                        .flux());
    }

    private static RuntimeContext context(String key) {
        var separator = key.indexOf(':');
        return separator < 0
                ? RuntimeContext.of(key, key)
                : RuntimeContext.of(key.substring(0, separator), key.substring(separator + 1));
    }
}
