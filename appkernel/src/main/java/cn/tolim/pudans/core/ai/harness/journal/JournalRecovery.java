package cn.tolim.pudans.core.ai.harness.journal;

import cn.tolim.pudans.core.ai.harness.agent.AgentStateStore;
import cn.tolim.pudans.core.ai.harness.agent.RuntimeContext;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class JournalRecovery implements SmartLifecycle {

    private static final Duration STALE_AFTER = Duration.ofMinutes(30);

    private final ThreadJournalStore journalStore;

    private final AgentStateStore stateStore;

    private volatile boolean running;

    private Disposable recovery;

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        recovery = recoverStale()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.warn("线程日志启动恢复失败。", error));
    }

    Mono<Void> recoverStale() {
        return journalStore.staleActiveThreads(STALE_AFTER).concatMap(this::recover).then();
    }

    private Mono<Void> recover(RuntimeContext context) {
        return journalStore
                .open(context)
                .flatMap(journal -> ThreadRecovery.abandonInterrupted(journal, "replica-startup"))
                .flatMap(projection -> projection.state() == null
                        ? Mono.<Void>empty()
                        : stateStore.save(context.userId(), context.sessionId(), projection.state()))
                .onErrorResume(error -> {
                    log.warn("线程日志恢复失败：{}。", context.key(), error);
                    return Mono.empty();
                });
    }

    @Override
    public void stop() {
        running = false;
        if (recovery != null) {
            recovery.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
