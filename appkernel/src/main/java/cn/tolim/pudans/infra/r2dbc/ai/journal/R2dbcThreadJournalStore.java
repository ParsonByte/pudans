package cn.tolim.pudans.infra.r2dbc.ai.journal;

import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import cn.tolim.pudans.core.ai.harness.journal.JournalEvent;
import cn.tolim.pudans.core.ai.harness.journal.JournalEventDraft;
import cn.tolim.pudans.core.ai.harness.journal.JournalException;
import cn.tolim.pudans.core.ai.harness.journal.JournalHead;
import cn.tolim.pudans.core.ai.harness.journal.ThreadJournal;
import cn.tolim.pudans.core.ai.harness.journal.ThreadJournalStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class R2dbcThreadJournalStore implements ThreadJournalStore {

    private final AiHarnessJournalRepository repository;

    @Override
    public Mono<ThreadJournal> open(RuntimeContext context) {
        Objects.requireNonNull(context, "运行时上下文不能为空");
        return Mono.<ThreadJournal>just(
                new R2dbcThreadJournal(repository, context.userId(), context.sessionId()));
    }

    @Override
    public Flux<RuntimeContext> staleActiveThreads(Duration staleAfter) {
        Objects.requireNonNull(staleAfter, "过期时长不能为空");
        return repository.staleActiveThreads(Instant.now().minus(staleAfter));
    }

    private static final class R2dbcThreadJournal implements ThreadJournal {

        private final AiHarnessJournalRepository repository;

        private final String userId;

        private final String sessionId;

        private R2dbcThreadJournal(
                AiHarnessJournalRepository repository, String userId, String sessionId) {
            this.repository = repository;
            this.userId = userId;
            this.sessionId = sessionId;
        }

        @Override
        public Mono<JournalHead> head() {
            return repository.head(userId, sessionId);
        }

        @Override
        public Mono<List<JournalEvent>> append(
                long expectedSeq, List<JournalEventDraft> drafts) {
            if (expectedSeq < 0) {
                return Mono.error(new JournalException("线程日志期望序号不能为负数。"));
            }
            if (drafts == null || drafts.isEmpty()) {
                return Mono.just(List.of());
            }
            return repository.append(userId, sessionId, expectedSeq, drafts);
        }

        @Override
        public Flux<JournalEvent> scan(long after, long through) {
            if (after < 0 || through < after) {
                return Flux.error(new IllegalArgumentException("线程日志扫描区间无效。"));
            }
            return repository.scan(userId, sessionId, after, through);
        }
    }
}
