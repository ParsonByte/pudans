package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import reactor.core.publisher.Mono;

public final class InMemoryThreadJournalStore implements ThreadJournalStore {

    private final ConcurrentMap<String, InMemoryThreadJournal> journals = new ConcurrentHashMap<>();

    @Override
    public Mono<ThreadJournal> open(RuntimeContext context) {
        Objects.requireNonNull(context, "运行时上下文不能为空");
        return Mono.<ThreadJournal>fromSupplier(
                () -> journals.computeIfAbsent(context.key(), ignored -> new InMemoryThreadJournal()));
    }
}