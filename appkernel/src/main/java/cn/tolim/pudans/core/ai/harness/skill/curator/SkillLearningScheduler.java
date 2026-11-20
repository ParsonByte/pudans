package cn.tolim.pudans.core.ai.harness.skill.curator;

import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 每小时评估一次草稿技能，达到使用门槛后自动晋升
@Slf4j
@Component
@RequiredArgsConstructor
public class SkillLearningScheduler implements SmartLifecycle {

    private static final Duration INTERVAL = Duration.ofHours(1);

    private final SkillCuratorService curatorService;

    private volatile boolean running;

    private Disposable learning;

    Mono<Long> learnOnce() {
        return curatorService.autoPromote();
    }

    @Override
    public void start() {
        if (running) {
            return;
        }
        running = true;
        learning = Flux.interval(INTERVAL)
                .concatMap(tick -> learnOnce()
                        .doOnNext(promoted -> {
                            if (promoted > 0) {
                                log.info("技能自学习晋升 {} 个草稿。", promoted);
                            }
                        })
                        .onErrorResume(error -> {
                            log.warn("技能自学习评估失败。", error);
                            return Mono.empty();
                        }))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(null, error -> log.error("技能自学习调度异常退出。", error));
    }

    @Override
    public void stop() {
        running = false;
        if (learning != null) {
            learning.dispose();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}