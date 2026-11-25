package cn.tolim.pudans.core.agent.memory;

import cn.tolim.pudans.infra.redis.ReactiveRedisTokenLease.TokenLease;
import cn.tolim.pudans.infra.r2dbc.agent.memory.MemoryJobEntity;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryDailyConsolidation {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");

    private static final String LEASE_LOST = "memory-daily-lease-lost";

    private final MemoryDailyLease lease;

    private final MemoryJobs jobs;

    private final MemoryJobWorker worker;

    @Scheduled(
            cron = "${pudans.memory.daily-cron:0 30 3 * * *}",
            zone = "${pudans.memory.timezone:Asia/Shanghai}")
    public void schedule() {
        runDaily()
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        ignored -> {},
                        error -> log.error("每日记忆汇池调度失败", error));
    }

    public Mono<Void> runDaily() {
        var today = LocalDate.now(DEFAULT_ZONE);
        return lease
                .acquire()
                .flatMap(token -> jobs
                        .createDailyGlobal(today)
                        .flatMap(job -> consolidate(job, token))
                        .onErrorResume(error -> release(token).then(Mono.error(error))));
    }

    private Mono<Void> consolidate(MemoryJobEntity job, TokenLease token) {
        var lost = new AtomicBoolean();
        var leaseLost = Sinks.<Void>empty();
        var heartbeat = lease
                .heartbeats(token)
                .doOnNext(alive -> {
                    if (!Boolean.TRUE.equals(alive)) {
                        lost.set(true);
                        leaseLost.tryEmitEmpty();
                    }
                })
                .onErrorResume(error -> {
                    log.warn("每日记忆汇池租约心跳失败：job={}", job.getId(), error);
                    lost.set(true);
                    leaseLost.tryEmitEmpty();
                    return Mono.empty();
                })
                .subscribe();
        return worker
                .process(job)
                .takeUntilOther(leaseLost.asMono())
                .then(Mono.defer(() -> lost.get()
                        ? jobs.fail(job.getId(), LEASE_LOST, "每日记忆汇池租约已丢失。")
                        : Mono.empty()))
                .doFinally(signal -> heartbeat.dispose())
                .then(release(token));
    }

    private Mono<Void> release(TokenLease token) {
        return lease.release(token).then();
    }
}
