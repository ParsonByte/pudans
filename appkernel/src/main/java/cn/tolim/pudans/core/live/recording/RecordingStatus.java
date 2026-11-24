package cn.tolim.pudans.core.live.recording;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_NOT_ACTIVE;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_NOT_FOUND;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_NOT_READY;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.core.live.alert.LiveAlerts;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.extension.contract.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

// 录制状态唯一写入口：每次读取最新版本再更新，版本冲突短退避重试
@Slf4j
@Component
@RequiredArgsConstructor
public class RecordingStatus {

    private static final List<LiveErrorCode> FAILURE_PRIORITY = List.of(
            LiveErrorCode.LIVE_STORAGE_POLICY_INVALID,
            LiveErrorCode.LIVE_DISK_FULL,
            LiveErrorCode.LIVE_STORAGE_BACKLOG,
            LiveErrorCode.LIVE_MERGE_FAILED,
            LiveErrorCode.LIVE_STORAGE_FAILED,
            LiveErrorCode.LIVE_STREAM_FAILED,
            LiveErrorCode.LIVE_RESOLVE_FAILED,
            LiveErrorCode.LIVE_RATE_LIMITED,
            LiveErrorCode.LIVE_STOP_TIMEOUT,
            LiveErrorCode.LIVE_RECORDER_LOST,
            LiveErrorCode.LIVE_NO_DATA);

    private final ReactiveExtensionClient client;

    // 失败状态要对外发事件，复用统一的去重告警入口
    private final LiveAlerts liveAlerts;

    // 失败数在内存维护，随状态迁移同步增减
    private final LiveMetrics metrics;

    // 读最新版本后应用修改，版本冲突短暂重试
    public Mono<LiveRecording> mutate(String name, Consumer<LiveRecording.Status> mutation) {
        return fetchRequired(name)
                .flatMap(recording -> {
                    mutation.accept(recording.getStatus());
                    return client.update(recording);
                })
                .retryWhen(conflictRetry());
    }

    // 定位成品后应用修改，缺失即视为未就绪
    public Mono<LiveRecording> mutatePart(String name, int index, Consumer<LiveRecording.Part> mutation) {
        return fetchRequired(name)
                .flatMap(recording -> {
                    var part = findPart(recording, index);
                    if (part == null) {
                        return Mono.error(LIVE_RECORDING_NOT_READY.exception());
                    }
                    mutation.accept(part);
                    return client.update(recording);
                })
                .retryWhen(conflictRetry());
    }

    // 按索引替换或追加成品并保持升序
    public Mono<LiveRecording> addPart(String name, LiveRecording.Part part) {
        return fetchRequired(name)
                .flatMap(recording -> {
                    var status = recording.getStatus();
                    var parts = status.getParts();
                    parts.removeIf(existing -> existing.getIndex() == part.getIndex());
                    parts.add(part);
                    parts.sort(java.util.Comparator.comparingInt(LiveRecording.Part::getIndex));
                    status.setPartCount(parts.size());
                    return client.update(recording);
                })
                .retryWhen(conflictRetry());
    }

    // 终态不可跨状态迁移，进入录制时维护起止时间
    public Mono<LiveRecording> transition(String name, LiveRecordingState state, String message) {
        return mutate(name, status -> {
            if (isTerminal(status.getState()) && status.getState() != state) {
                throw LIVE_RECORDING_NOT_ACTIVE.exception();
            }
            status.setState(state);
            status.setMessage(message);
            if (state == LiveRecordingState.RECORDING) {
                if (status.getStartedAt() == null) {
                    status.setStartedAt(Instant.now());
                }
                if (status.getEndedAt() != null) {
                    status.setEndedAt(null);
                }
            }
            if (state == LiveRecordingState.COMPLETED) {
                if (status.getEndedAt() == null) {
                    status.setEndedAt(Instant.now());
                }
                status.setErrorCode(null);
            }
        });
    }

    // 续传成功后允许失败录制补记完成，并移出失败计数
    public Mono<LiveRecording> completeAfterResume(String name, Consumer<LiveRecording.Status> mutation) {
        return fetchRequired(name)
                .flatMap(recording -> {
                    var status = recording.getStatus();
                    if (status.getState() != LiveRecordingState.FAILED) {
                        return Mono.error(LIVE_RECORDING_NOT_READY.exception());
                    }
                    mutation.accept(status);
                    status.setState(LiveRecordingState.COMPLETED);
                    status.setMessage("续传完成");
                    status.setErrorCode(null);
                    if (status.getEndedAt() == null) {
                        status.setEndedAt(Instant.now());
                    }
                    return client.update(recording);
                })
                .doOnNext(updated -> metrics.recordingRemoved(updated.getMetadata().getName()))
                .retryWhen(conflictRetry());
    }

    // 多错误并存时保留最能阻塞恢复的错误码
    public Mono<LiveRecording> fail(String name, LiveErrorCode code, String message) {
        return fetchRequired(name)
                .flatMap(recording -> {
                    var status = recording.getStatus();
                    if (isTerminal(status.getState()) && status.getState() != LiveRecordingState.FAILED) {
                        return Mono.just(recording);
                    }
                    // 只有首次进入失败状态才上报，重复失败只更新错误码
                    var wasFailed = status.getState() == LiveRecordingState.FAILED;
                    status.setState(LiveRecordingState.FAILED);
                    if (status.getEndedAt() == null) {
                        status.setEndedAt(Instant.now());
                    }
                    var existing = status.getErrorCode();
                    if (existing == null || priority(code.name()) < priority(existing)) {
                        status.setErrorCode(code.name());
                        status.setMessage(message == null ? code.message() : message);
                    }
                    // 写入成功后再上报，防止告警与真实状态不一致
                    return client.update(recording).doOnNext(updated -> {
                        if (!wasFailed) {
                            notifyFailure(updated);
                        }
                    });
                })
                .retryWhen(conflictRetry());
    }

    // 首次进入失败状态时计数并异步发送失败告警
    private void notifyFailure(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        metrics.recordingFailed(name);
        var payload = new LinkedHashMap<String, Object>();
        payload.put("recordingName", name);
        var spec = recording.getSpec();
        payload.put("sourceName", spec == null ? null : spec.getSourceName());
        payload.put("roomId", spec == null ? null : spec.getRoomId());
        payload.put("errorCode", recording.getStatus().getErrorCode());
        payload.put("message", recording.getStatus().getMessage());
        payload.put("at", Instant.now().toString());
        try {
            liveAlerts
                    .alert("live.recording.failed", name, payload)
                    .onErrorResume(error -> {
                        log.warn("直播录制失败告警发送失败 [{}]：{}", name, error.getMessage());
                        return Mono.empty();
                    })
                    .subscribe();
        } catch (RuntimeException error) {
            log.warn("直播录制失败告警发送失败 [{}]：{}", name, error.getMessage());
        }
    }

    // 查询录制，不存在时返回空
    public Mono<LiveRecording> find(String name) {
        return client.fetch(LiveRecording.class, name);
    }

    // 查询录制，不存在时抛未找到错误
    public Mono<LiveRecording> findRequired(String name) {
        return fetchRequired(name);
    }

    // 统一把缺失录制转换为未找到错误
    private Mono<LiveRecording> fetchRequired(String name) {
        return client.fetch(LiveRecording.class, name).switchIfEmpty(Mono.error(LIVE_RECORDING_NOT_FOUND.exception()));
    }

    // 在录制状态中按索引查找成品
    public static LiveRecording.Part findPart(LiveRecording recording, int index) {
        if (recording == null || recording.getStatus() == null || recording.getStatus().getParts() == null) {
            return null;
        }
        for (var part : recording.getStatus().getParts()) {
            if (part.getIndex() == index) {
                return part;
            }
        }
        return null;
    }

    // 完成、失败与取消都是终态
    private static boolean isTerminal(LiveRecordingState state) {
        return state == LiveRecordingState.COMPLETED
                || state == LiveRecordingState.FAILED
                || state == LiveRecordingState.CANCELED;
    }

    // 错误码越靠前越能阻塞恢复，数值越小优先级越高
    private static int priority(String errorCode) {
        for (int i = 0; i < FAILURE_PRIORITY.size(); i++) {
            if (FAILURE_PRIORITY.get(i).name().equals(errorCode)) {
                return i;
            }
        }
        return FAILURE_PRIORITY.size();
    }

    // 只对乐观锁冲突重试，其他错误立即上抛
    private static Retry conflictRetry() {
        return Retry.backoff(3, Duration.ofMillis(50))
                .maxBackoff(Duration.ofMillis(300))
                .jitter(0)
                .filter(OptimisticLockingFailureException.class::isInstance);
    }
}
