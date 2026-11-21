package cn.tolim.pudans.core.ai.memory;

import cn.tolim.pudans.contract.ai.resource.AiSetting;
import cn.tolim.pudans.core.ai.config.AiSettings;
import cn.tolim.pudans.infra.r2dbc.ai.memory.AiMemoryL1Repository;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryMaintenanceScheduler implements SmartLifecycle {

    private static final Duration DECAY_INTERVAL = Duration.ofHours(1);

    private final AiSettings aiSettings;

    private final AiMemoryL1Repository l1Repository;

    private volatile boolean running;

    private Disposable decay;

    Mono<Long> decayOnce() {
        return aiSettings
                .spec()
                .map(spec -> spec.getMemory() == null ? new AiSetting.Memory() : spec.getMemory())
                .filter(AiSetting.Memory::isDecayEnabled)
                .flatMap(memory -> l1Repository.applyDecay(
                        Math.max(0, memory.getDecayDailyAmount()),
                        Math.max(0, memory.getDecayMinScore()),
                        Math.max(1, memory.getDecayStaleDays())))
                .defaultIfEmpty(0L);
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        decay = Flux.interval(DECAY_INTERVAL)
                .concatMap(tick -> decayOnce()
                        .doOnNext(updated -> {
                            if (updated > 0) {
                                log.info("记忆衰减更新 {} 条。", updated);
                            }
                        })
                        .onErrorResume(error -> {
                            log.warn("记忆衰减任务失败。", error);
                            return Mono.empty();
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.error("记忆衰减调度异常退出。", error));
    }

    @Override
    public void stop() {
        running = false;
        if (decay != null) {
            decay.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}