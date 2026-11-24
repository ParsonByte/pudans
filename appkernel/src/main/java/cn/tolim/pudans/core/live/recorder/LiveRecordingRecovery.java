package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.extension.contract.ExtensionUtil.notDeleting;
import static cn.tolim.pudans.extension.contract.index.query.Queries.in;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.extension.contract.ListOptions;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.live.source.SourceStatus;
import cn.tolim.pudans.infra.properties.LiveProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 启动时收尾失联实例遗留的录制，标记失败并释放心跳与停止键
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRecordingRecovery {

    public static final String OWNER_KEY_PREFIX = "pudans:live:recording:owner:";

    public static final String STOP_KEY_PREFIX = "pudans:live:recording:stop:";

    public static final String RECOVERY_KEY_PREFIX = "pudans:live:recovery:";

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(90);

    private static final Duration QUEUE_TIMEOUT = Duration.ofMinutes(30);

    private final ReactiveExtensionClient client;

    private final RecordingStatus recordingStatus;

    private final LiveRecorderRegistry registry;

    private final LiveRecordingWorkspace workspace;

    private final SourceStatus sourceStatus;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final LiveProperties properties;

    // 失败计数存在内存里，重启后需要从录制列表回填
    private final LiveMetrics metrics;

    private final String instanceId = UUID.randomUUID().toString();

    // 就绪后再恢复，Scheme 注册完成早于该事件，构造期扫描会因缺少 Scheme 直接失败
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent ignored) {
        Schedulers.boundedElastic().schedule(() -> recover().subscribe(
                count -> {
                    if (count > 0) {
                        log.info("直播录制启动恢复处理 {} 条失联录制", count);
                    }
                },
                error -> log.warn("直播录制启动恢复失败：{}", error.getMessage())));
        // 与活动录制恢复并行回填失败计数，避免启动拖慢恢复
        Schedulers.boundedElastic().schedule(() -> seedFailedMetrics().subscribe(
                count -> {
                    if (count > 0) {
                        log.info("直播录制启动加载 {} 条失败录制指标", count);
                    }
                },
                error -> log.warn("直播录制失败指标加载失败：{}", error.getMessage())));
    }

    // 启动时把历史失败录制装入失败仪表，避免重启后计数归零
    public Mono<Integer> seedFailedMetrics() {
        var options = ListOptions.builder()
                .andQuery(in("status.state", List.of(LiveRecordingState.FAILED)))
                .andQuery(notDeleting())
                .build();
        return client.listAll(LiveRecording.class, options, Sort.by(Sort.Order.asc("spec.startedAt")))
                .doOnNext(recording -> metrics.recordingFailed(recording.getMetadata().getName()))
                .count()
                .map(Long::intValue);
    }

    // 分页扫描活动录制，逐条尝试恢复并计数
    public Mono<Integer> recover() {
        var options = ListOptions.builder()
                .andQuery(in("status.state", List.of(
                        LiveRecordingState.QUEUED, LiveRecordingState.RECORDING, LiveRecordingState.FINALIZING)))
                .andQuery(notDeleting())
                .build();
        return client.listAll(LiveRecording.class, options, Sort.by(Sort.Order.asc("spec.startedAt")))
                .concatMap(this::recoverOne)
                .reduce(0, Integer::sum);
    }

    // 跳过本实例活动、心跳新鲜与未超时的排队录制
    private Mono<Integer> recoverOne(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        if (registry.isActive(name)) {
            return Mono.just(0);
        }
        var status = recording.getStatus();
        if (isHeartbeatFresh(status)) {
            return Mono.just(0);
        }
        var state = status == null ? null : status.getState();
        if (state == LiveRecordingState.QUEUED && !isQueueExpired(recording)) {
            return Mono.just(0);
        }
        return acquireLock(name).flatMap(locked -> locked ? failLost(recording) : Mono.just(0));
    }

    // 抢不到锁说明别的实例正在处理，重复恢复由状态更新乐观锁兜底
    private Mono<Boolean> acquireLock(String name) {
        return redisTemplate
                .opsForValue()
                .setIfAbsent(
                        RECOVERY_KEY_PREFIX + name,
                        instanceId,
                        properties.getRecorder().getRecoveryLockTtl())
                .onErrorResume(error -> {
                    log.warn("获取录制恢复锁失败，降级继续恢复 [{}]：{}", name, error.getMessage());
                    return Mono.just(Boolean.TRUE);
                });
    }

    // 标记失联失败并清理键、空目录与源指针
    private Mono<Integer> failLost(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        var state = recording.getStatus() == null ? null : recording.getStatus().getState();
        var message = "录制器实例失联，录制中断（原状态 " + state + "）";
        return recordingStatus
                .fail(name, LiveErrorCode.LIVE_RECORDER_LOST, message)
                .then(cleanKeys(name))
                .then(cleanEmptyDir(name))
                .then(clearSource(recording))
                .thenReturn(1)
                .onErrorResume(error -> {
                    log.warn("恢复失联录制失败 [{}]：{}", name, error.getMessage());
                    return Mono.just(0);
                });
    }

    // 释放直播源的录制指针，避免失联录制永久阻塞该直播源
    private Mono<Void> clearSource(LiveRecording recording) {
        var sourceName = recording.getSpec() == null ? null : recording.getSpec().getSourceName();
        if (!org.springframework.util.StringUtils.hasText(sourceName)) {
            return Mono.empty();
        }
        return sourceStatus.recordingEnded(sourceName).onErrorResume(error -> {
            log.warn("清理直播源录制状态失败 [{}]：{}", sourceName, error.getMessage());
            return Mono.empty();
        }).then();
    }

    // 删除该录制的 owner 与停止键
    private Mono<Void> cleanKeys(String name) {
        return redisTemplate
                .delete(Flux.just(OWNER_KEY_PREFIX + name, STOP_KEY_PREFIX + name))
                .onErrorResume(error -> {
                    log.warn("清理录制心跳键失败 [{}]：{}", name, error.getMessage());
                    return Mono.empty();
                })
                .then();
    }

    // 没有残留文件的空目录直接删除，有数据的目录保留给删除流程处理
    private Mono<Void> cleanEmptyDir(String name) {
        return Mono.fromRunnable(() -> {
                    var dir = workspace.recordingDir(name);
                    if (!Files.isDirectory(dir) || !isEmpty(dir)) {
                        return;
                    }
                    workspace.deleteRecordingDir(name);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    // 目录不可读时按非空处理，避免误删
    private static boolean isEmpty(Path dir) {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.findAny().isEmpty();
        } catch (IOException error) {
            return false;
        }
    }

    // 心跳新鲜说明录制器实例仍在工作，即使不是本实例也交给它收尾
    public static boolean isHeartbeatFresh(LiveRecording.Status status) {
        if (status == null || status.getHeartbeatAt() == null) {
            return false;
        }
        return status.getHeartbeatAt().plus(HEARTBEAT_TIMEOUT).isAfter(Instant.now());
    }

    // 排队超过时限仍未开始即视为可恢复
    public static boolean isQueueExpired(LiveRecording recording) {
        var created = recording.getMetadata().getCreationTimestamp();
        return created == null || created.plus(QUEUE_TIMEOUT).isBefore(Instant.now());
    }
}
