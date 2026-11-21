package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_LIMIT_REACHED;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_NOT_ACTIVE;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STOP_TIMEOUT;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import cn.tolim.pudans.core.live.source.SourceStatus;
import cn.tolim.pudans.infra.properties.LiveProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

// 录制任务的生命周期入口：登记、准入、停止与失败后本地数据续传
@Slf4j
@Component
@RequiredArgsConstructor
public class LiveRecorder {

    private final LiveProperties properties;

    private final RecordingStatus recordingStatus;

    private final LiveRecorderRegistry registry;

    private final LiveRecorderAdmission admission;

    private final LivePartMerger merger;

    private final LiveRecordingWorkspace workspace;

    private final LiveSegmentManifest segmentManifest;

    private final LiveStreamOpener opener;

    private final RoomResolver roomResolver;

    private final LiveDiskGuard diskGuard;

    private final LiveRecorderBackoff backoff;

    private final LiveStoragePolicyResolver policyResolver;

    private final LiveMetrics metrics;

    private final ReactiveStringRedisTemplate redisTemplate;

    private final SourceStatus sourceStatus;

    public Mono<LiveRecording> launch(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        var task = new LiveRecorderTask(
                properties,
                recordingStatus,
                registry,
                merger,
                workspace,
                segmentManifest,
                opener,
                roomResolver,
                diskGuard,
                backoff,
                policyResolver,
                metrics,
                redisTemplate,
                sourceStatus,
                recording);
        registry.register(name, task);
        var result = admission.admit(name, task);
        if (result == LiveRecorderAdmission.Admission.REJECTED) {
            registry.unregister(name);
            log.warn("录制并发与队列均已满，拒绝创建 [{}]", name);
            return recordingStatus.fail(name, LIVE_RECORDING_LIMIT_REACHED, LIVE_RECORDING_LIMIT_REACHED.message());
        }
        if (result == LiveRecorderAdmission.Admission.QUEUED) {
            return recordingStatus.mutate(name, status -> status.setMessage("等待录制槽位"));
        }
        return Mono.just(recording);
    }

    public Mono<LiveRecording> stop(LiveRecording recording, String requestedBy) {
        var name = recording.getMetadata().getName();
        return switch (recording.getStatus().getState()) {
            case QUEUED -> {
                admission.cancel(name);
                registry.unregister(name);
                yield clearRemoteKeys(name)
                        .then(recordingStatus.transition(name, LiveRecordingState.CANCELED, "排队中被取消"));
            }
            case RECORDING -> requestStopSignal(name, requestedBy)
                    .then(recordingStatus.mutate(name, status -> status.setStopRequestedAt(Instant.now())))
                    .flatMap(updated -> registry.requestStop(name).thenReturn(updated))
                    .flatMap(updated -> recordingStatus.transition(name, LiveRecordingState.FINALIZING, "收到停止请求"))
                    .doOnNext(updated -> scheduleStopWatchdog(name));
            case FINALIZING -> requestStopSignal(name, requestedBy)
                    .thenReturn(recording)
                    .doOnNext(updated -> scheduleStopWatchdog(name));
            default -> Mono.error(LIVE_RECORDING_NOT_ACTIVE.exception());
        };
    }

    public boolean hasLocalData(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        if (!workspace.listSegmentFiles(name).isEmpty() || !workspace.listPartFiles(name).isEmpty()) {
            return true;
        }
        return recording.getStatus().getParts().stream().anyMatch(part -> part.getState() != PartState.UPLOADED);
    }

    public Mono<LiveRecording> resumeUpload(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        return Mono.fromRunnable(() -> {
                    merger.start(name, 0);
                    merger.requestMerge(name, true);
                    try {
                        merger.finalizeRecording(name).block();
                    } catch (RuntimeException error) {
                        if (error.getCause() instanceof TimeoutException) {
                            log.warn("录制续传收尾超时 [{}]", name);
                        } else {
                            log.warn("录制续传收尾失败 [{}]：{}", name, error.getMessage());
                        }
                    } finally {
                        merger.stop(name);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then(recordingStatus.findRequired(name));
    }

    private void scheduleStopWatchdog(String name) {
        Mono.delay(properties.getRecorder().getStopTimeout(), Schedulers.boundedElastic())
                .flatMap(ignored -> {
                    if (!registry.isStreaming(name)) {
                        return Mono.empty();
                    }
                    log.warn("录制停止超时，强制关闭拉流器 [{}]", name);
                    return registry
                            .forceStop(name)
                            .then(recordingStatus.fail(name, LIVE_STOP_TIMEOUT, LIVE_STOP_TIMEOUT.message()))
                            .then();
                })
                .subscribe(ignored -> {}, error -> log.warn("停止看门狗执行失败 [{}]：{}", name, error.getMessage()));
    }

    private Mono<Void> requestStopSignal(String name, String requestedBy) {
        var actor = requestedBy == null || requestedBy.isBlank() ? "unknown" : requestedBy;
        return redisTemplate
                .opsForValue()
                .set(LiveRecordingRecovery.STOP_KEY_PREFIX + name, actor, Duration.ofHours(1))
                .then()
                .onErrorResume(error -> {
                    log.warn("写入跨实例停止信号失败 [{}]：{}", name, error.getMessage());
                    return Mono.empty();
                });
    }

    private Mono<Void> clearRemoteKeys(String name) {
        return redisTemplate
                .delete(
                        LiveRecordingRecovery.OWNER_KEY_PREFIX + name,
                        LiveRecordingRecovery.STOP_KEY_PREFIX + name)
                .then()
                .onErrorResume(error -> {
                    log.warn("清理录制 Redis 键失败 [{}]：{}", name, error.getMessage());
                    return Mono.empty();
                });
    }
}
