package cn.tolim.pudans.core.ai.plan;

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
public class PlanQuotaSweepScheduler implements SmartLifecycle {

    private static final Duration SWEEP_INTERVAL = Duration.ofMinutes(5);

    private final PlanQuotas planQuotas;

    private volatile boolean running;

    private Disposable sweep;

    Mono<PlanQuotas.SweepResult> sweepOnce() {
        return planQuotas.sweepExpired();
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        sweep = Flux.interval(SWEEP_INTERVAL)
                .concatMap(tick -> sweepOnce()
                        .doOnNext(result -> {
                            if (result.releasedCount() > 0) {
                                log.info("释放过期套餐预留：{} 笔。", result.releasedCount());
                            }
                        })
                        .onErrorResume(error -> {
                            log.warn("套餐预留清理失败。", error);
                            return Mono.empty();
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.error("套餐预留清理调度异常退出。", error));
    }

    @Override
    public void stop() {
        running = false;
        if (sweep != null) {
            sweep.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}