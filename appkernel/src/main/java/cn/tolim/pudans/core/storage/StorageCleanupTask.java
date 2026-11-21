package cn.tolim.pudans.core.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.infra.properties.PudansProperties;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class StorageCleanupTask {

    private static final int BATCH_SIZE = 100;

    private final FileRecords fileRecords;

    private final Uploads uploads;

    private final StorageTransfer storageTransfer;

    private final PudansProperties properties;

    private Disposable subscription;

    @PostConstruct
    public void start() {
        var interval = Duration.ofSeconds(Math.max(10, properties.getStorage().getCleanupIntervalSeconds()));
        subscription = Flux.interval(interval, interval)
                .onBackpressureDrop()
                .concatMap(ignored -> cleanup())
                .onErrorContinue((error, ignored) -> log.error("存储清理任务失败", error))
                .subscribe();
    }

    Mono<Void> cleanup() {
        var now = Instant.now();
        return fileRecords
                .listExpiredUploads(now, BATCH_SIZE)
                .concatMap(file -> uploads.abort(file.getMetadata().getName())
                        .onErrorResume(error -> {
                            log.warn("清理过期上传 [{}] 失败", file.getMetadata().getName(), error);
                            return Mono.empty();
                        }))
                .thenMany(fileRecords
                        .listExpiredTrash(now, BATCH_SIZE)
                        .concatMap(file -> storageTransfer.purge(file.getMetadata().getName())
                                .onErrorResume(error -> {
                                    log.warn("清理回收站文件 [{}] 失败", file.getMetadata().getName(), error);
                                    return Mono.empty();
                                })))
                .then();
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
