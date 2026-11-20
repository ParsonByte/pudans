package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import java.time.Duration;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ThreadJournalStore {

    Mono<ThreadJournal> open(RuntimeContext context);

    Flux<RuntimeContext> staleActiveThreads(Duration staleAfter);
}
