package cn.tolim.pudans.core.infra.audit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;


@Slf4j
@Component
@RequiredArgsConstructor
public class AuditArchiveTask {

    private final AuditStore auditStore;

    @Value("${pudans.audit.retention-days:90}")
    private long retentionDays;

    @Value("${pudans.audit.archive-interval-seconds:21600}")
    private long archiveIntervalSeconds;

    private Disposable subscription;

    @PostConstruct
    public void start() {
        subscription =
                Flux.interval(Duration.ofSeconds(archiveIntervalSeconds), Duration.ofSeconds(archiveIntervalSeconds))
                        .onBackpressureDrop()
                        .concatMap(ignored -> archive())
                        .onErrorContinue((error, ignored) -> log.error("审计归档失败", error))
                        .subscribe();
    }

    Mono<Long> archive() {
        var cutoff = Instant.now().minus(Math.max(1, retentionDays), ChronoUnit.DAYS);
        return auditStore
                .archiveBefore(cutoff)
                .doOnNext(count -> log.info("已归档 {} 条早于 {} 的审计记录", count, cutoff));
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
