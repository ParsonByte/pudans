package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.extension.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.contract.extension.index.query.Queries.in;
import static cn.tolim.pudans.contract.extension.index.query.Queries.lessThan;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.core.extension.live.LiveRecording;
import cn.tolim.pudans.contract.core.extension.live.LiveRecordingState;
import cn.tolim.pudans.contract.extension.ListOptions;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.core.live.alert.LiveAlertService;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.LiveRecordingStatusService;
import cn.tolim.pudans.core.storage.StorageService;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 按保留期清理已结束的录制记录与对象，启动时顺带清理无人认领的临时目录
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRecordingCleanupTask {

    private static final Duration ORPHAN_AGE = Duration.ofHours(24);

    private static final Duration DEFAULT_INTERVAL = Duration.ofHours(1);

    private final ReactiveExtensionClient client;

    private final StorageService storageService;

    private final LiveRecordingStatusService statusService;

    private final LiveRecordingWorkspace workspace;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final LiveProperties properties;

    private final LiveMetrics metrics;

    private final LiveAlertService alertService;

    private volatile Disposable subscription;

    @PostConstruct
    public void start() {
        var interval = interval();
        subscription = Flux.interval(interval, interval)
                .onBackpressureDrop()
                .concatMap(tick -> cleanupRetention())
                .onErrorContinue((error, ignored) -> log.error("直播录制保留清理失败：{}", error.getMessage()))
                .subscribe(count -> {
                    if (count > 0) {
                        log.info("直播录制保留清理删除 {} 条过期录制", count);
                    }
                });
        Schedulers.boundedElastic().schedule(() -> cleanupOrphanDirectories().subscribe(
                count -> {
                    if (count > 0) {
                        log.info("直播录制启动清理删除 {} 个孤儿目录", count);
                    }
                },
                error -> log.warn("直播录制孤儿目录清理失败：{}", error.getMessage())));
    }

    @PreDestroy
    public void stop() {
        var current = subscription;
        if (current != null) {
            current.dispose();
            subscription = null;
        }
    }

    public Mono<Integer> cleanupRetention() {
        var days = properties.getRetention().getDays();
        if (days <= 0) {
            return Mono.just(0);
        }
        var threshold = Instant.now().minus(Duration.ofDays(days));
        var options = ListOptions.builder()
                .andQuery(in("status.state", List.of(
                        LiveRecordingState.COMPLETED, LiveRecordingState.FAILED, LiveRecordingState.CANCELED)))
                .andQuery(lessThan("spec.startedAt", threshold))
                .andQuery(notDeleting())
                .build();
        return client.listAll(LiveRecording.class, options, Sort.by(Sort.Order.asc("spec.startedAt")))
                .filter(recording -> isExpired(recording, threshold))
                .take(properties.getRetention().getBatchSize())
                .concatMap(this::deleteExpired)
                .reduce(0, Integer::sum);
    }

    // 启动清理只删除既没有录制记录也没有心跳的目录，属于任何录制的目录交由 finalizer 处理
    public Mono<Integer> cleanupOrphanDirectories() {
        var workDir = workDir();
        if (workDir == null) {
            return Mono.just(0);
        }
        var deleted = new AtomicInteger();
        return Mono.fromCallable(() -> directories(workDir))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable)
                .filter(this::isOrphanCandidate)
                .filter(this::isRecordingDirectoryName)
                .concatMap(entry -> isTracked(entry.getFileName().toString()).flatMap(tracked -> {
                    if (tracked) {
                        return Mono.empty();
                    }
                    return Mono.fromRunnable(() -> {
                                workspace.deleteRecordingDir(entry.getFileName().toString());
                                deleted.incrementAndGet();
                                log.info("删除直播录制孤儿目录 [{}]", entry);
                            })
                            .subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(error -> {
                                log.warn("删除直播录制孤儿目录失败 [{}]：{}", entry, error.getMessage());
                                return Mono.empty();
                            });
                }))
                .then(Mono.fromSupplier(deleted::get));
    }

    private Mono<Integer> deleteExpired(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        return purgeObjects(recording)
                .then(Mono.fromRunnable(() -> deleteLocalDir(name)).subscribeOn(Schedulers.boundedElastic()))
                .then(Mono.fromRunnable(() -> client.delete(recording)).subscribeOn(Schedulers.boundedElastic()))
                .thenReturn(1)
                .onErrorResume(error -> retainFailed(recording, error).thenReturn(0));
    }

    private Mono<Void> purgeObjects(LiveRecording recording) {
        var status = recording.getStatus();
        var parts = status == null || status.getParts() == null ? List.<LiveRecording.Part>of() : status.getParts();
        return Flux.fromIterable(parts)
                .filter(part -> StringUtils.hasText(part.getStorageFile()))
                .concatMap(part -> storageService.purgeObjectFirst(part.getStorageFile()))
                .then();
    }

    private Mono<Void> retainFailed(LiveRecording recording, Throwable error) {
        var name = recording.getMetadata().getName();
        var code = error instanceof ApiException apiException ? apiException.getCode() : errorCode(error);
        metrics.cleanupFailed();
        log.warn("清理过期录制失败 [{}]：{}", name, error.getMessage());
        return statusService
                .mutate(name, status -> status.setCleanupAttempts(status.getCleanupAttempts() + 1))
                .onErrorResume(updateError -> {
                    log.warn("记录录制清理失败次数失败 [{}]：{}", name, updateError.getMessage());
                    return Mono.empty();
                })
                .then(alertService
                        .alert(
                                "live.cleanup.failed",
                                name,
                                Map.of("recordingName", name, "errorCode", code))
                        .onErrorResume(alertError -> Mono.empty()));
    }

    private void deleteLocalDir(String name) {
        if (Files.exists(workspace.recordingDir(name))) {
            workspace.deleteRecordingDir(name);
        }
    }

    private boolean isExpired(LiveRecording recording, Instant threshold) {
        var endedAt = effectiveEnd(recording);
        return endedAt != null && endedAt.isBefore(threshold);
    }

    private static Instant effectiveEnd(LiveRecording recording) {
        var status = recording.getStatus();
        if (status != null && status.getEndedAt() != null) {
            return status.getEndedAt();
        }
        if (status != null && status.getStartedAt() != null) {
            return status.getStartedAt();
        }
        var spec = recording.getSpec();
        if (spec != null && spec.getStartedAt() != null) {
            return spec.getStartedAt();
        }
        return recording.getMetadata().getCreationTimestamp();
    }

    // 复用工作目录的命名校验，非法名字不是录制目录
    private boolean isRecordingDirectoryName(Path entry) {
        try {
            workspace.recordingDir(entry.getFileName().toString());
            return true;
        } catch (IllegalArgumentException error) {
            return false;
        }
    }

    private boolean isOrphanCandidate(Path entry) {
        try {
            if (!Files.isDirectory(entry)) {
                return false;
            }
            var modified = Files.getLastModifiedTime(entry).toInstant();
            return modified.plus(ORPHAN_AGE).isBefore(Instant.now());
        } catch (IOException error) {
            log.warn("读取直播录制目录属性失败 [{}]：{}", entry, error.getMessage());
            return false;
        }
    }

    // Redis 心跳异常时保守跳过，避免误删仍在使用的目录
    private Mono<Boolean> isTracked(String name) {
        return client.fetch(LiveRecording.class, name)
                .hasElement()
                .flatMap(recorded -> recorded
                        ? Mono.just(true)
                        : redisTemplate
                                .opsForValue()
                                .get(LiveRecordingRecovery.OWNER_KEY_PREFIX + name)
                                .hasElement())
                .onErrorResume(error -> {
                    log.warn("查询直播录制归属失败，跳过目录清理 [{}]：{}", name, error.getMessage());
                    return Mono.just(true);
                });
    }

    private List<Path> directories(Path workDir) throws IOException {
        if (!Files.isDirectory(workDir)) {
            return List.of();
        }
        try (var stream = Files.list(workDir)) {
            return stream.filter(Files::isDirectory).toList();
        }
    }

    private Path workDir() {
        var configured = properties.getRecorder().getWorkDir();
        if (!StringUtils.hasText(configured)) {
            log.warn("未配置直播录制工作目录，跳过孤儿目录清理");
            return null;
        }
        return Path.of(configured);
    }

    private Duration interval() {
        var configured = properties.getRetention().getInterval();
        return configured == null || configured.isZero() || configured.isNegative()
                ? DEFAULT_INTERVAL
                : configured;
    }

    private static String errorCode(Throwable error) {
        return error.getClass().getSimpleName();
    }
}