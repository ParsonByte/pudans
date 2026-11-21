package cn.tolim.pudans.core.storage;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
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

    private static final Duration STALE_ARTIFACT_AGE = Duration.ofHours(24);

    private final FileRecords fileRecords;

    private final Uploads uploads;

    private final StorageTransfer storageTransfer;

    private final PudansProperties properties;

    private Disposable subscription;

    // 启动时清理残留文件，之后按周期清理过期上传与回收站。
    @PostConstruct
    public void start() {
        var interval = Duration.ofSeconds(Math.max(10, properties.getStorage().getCleanupIntervalSeconds()));
        subscription = Flux.concat(
                        Mono.defer(this::sweepLocalArtifacts)
                                .onErrorResume(error -> {
                                    log.warn("启动清理存储残留文件失败", error);
                                    return Mono.empty();
                                })
                                .then(),
                        Flux.interval(interval, interval).concatMap(ignored -> cleanup()))
                .onErrorContinue((error, ignored) -> log.error("存储清理任务失败", error))
                .subscribe();
    }

    // 清理没有活动上传引用的本地临时文件与会话目录。
    Mono<Void> sweepLocalArtifacts() {
        var cutoff = Instant.now().minus(STALE_ARTIFACT_AGE);
        return fileRecords
                .listUploadingNames()
                .collect(Collectors.toSet())
                .flatMap(active -> uploads.cleanupLocalArtifacts(active, cutoff));
    }

    // 分批清理过期上传与到期回收站文件。
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

    // 应用关闭时停止清理订阅。
    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }
}
