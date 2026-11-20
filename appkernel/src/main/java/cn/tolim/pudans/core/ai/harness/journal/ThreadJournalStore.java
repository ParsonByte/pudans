package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import reactor.core.publisher.Mono;

public interface ThreadJournalStore {

    Mono<ThreadJournal> open(RuntimeContext context);
}