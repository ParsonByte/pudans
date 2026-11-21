package cn.tolim.pudans.core.live.recording;

import static cn.tolim.pudans.extension.api.ExtensionUtil.defaultSort;
import static cn.tolim.pudans.extension.api.index.query.Queries.in;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_INVALID_ARGUMENT;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_INVALID_SPEC;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_ACTIVE;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_RECORDING_NOT_READY;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_ROOM_OFFLINE;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_SOURCE_NOT_FOUND;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.LiveRecordingFormat;
import cn.tolim.pudans.contract.live.LiveRecordingState;
import cn.tolim.pudans.contract.live.LiveSource;
import cn.tolim.pudans.contract.live.LiveSourceState;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.extension.api.ListOptions;
import cn.tolim.pudans.extension.api.Metadata;
import cn.tolim.pudans.extension.api.ReactiveExtensionClient;
import cn.tolim.pudans.contract.live.dto.LiveRoomInfo;
import cn.tolim.pudans.contract.live.dto.LiveRecordingRetry;
import cn.tolim.pudans.core.live.recorder.LiveRecorderRegistry;
import cn.tolim.pudans.core.live.recorder.LiveRecorder;
import cn.tolim.pudans.core.live.recorder.LiveStoragePolicyResolver;
import cn.tolim.pudans.core.live.resolve.RoomResolver;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

// 录制记录的对外服务：创建、停止、失败重试、查询与活动列表
@Slf4j
@Component
@RequiredArgsConstructor
public class Recordings {

    private static final String RECORDING_NAME_PREFIX = "rec-";

    private static final int NAME_RANDOM_LENGTH = 4;

    private static final char[] NAME_RANDOM_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static final int CONFLICT_RETRIES = 3;

    private static final long CONFLICT_BACKOFF_MILLIS = 50L;

    private static final long CONFLICT_MAX_BACKOFF_MILLIS = 300L;

    private static final String MODE_UPLOAD = "UPLOAD";

    private static final String MODE_STREAM = "STREAM";

    private static final List<LiveRecordingState> ACTIVE_STATES =
            List.of(LiveRecordingState.QUEUED, LiveRecordingState.RECORDING, LiveRecordingState.FINALIZING);

    private static final DateTimeFormatter NAME_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReactiveExtensionClient client;

    private final RecordingStatus recordingStatus;

    private final LiveStoragePolicyResolver policyResolver;

    private final LiveRecorder liveRecorder;

    private final LiveRecorderRegistry registry;

    private final RoomResolver roomResolver;

    public Mono<LiveRecording> start(LiveSource source, LiveRoomInfo info, boolean auto, String requestedBy) {
        return start(source, info, auto, requestedBy, null);
    }

    public Mono<LiveRecording> start(
            LiveSource source, LiveRoomInfo info, boolean auto, String requestedBy, String retryOf) {
        if (source == null || source.getMetadata() == null || !StringUtils.hasText(source.getMetadata().getName())) {
            return Mono.error(LIVE_INVALID_SPEC.exception());
        }
        if (source.getSpec() == null || source.getSpec().getPlatform() == null) {
            return Mono.error(LIVE_INVALID_SPEC.exception());
        }
        if (info == null) {
            return Mono.error(LIVE_INVALID_ARGUMENT.exception());
        }
        if (!info.live()) {
            return Mono.error(LIVE_ROOM_OFFLINE.exception());
        }
        var sourceName = source.getMetadata().getName();
        var currentRecording = source.getStatus() == null ? null : source.getStatus().getCurrentRecording();
        if (StringUtils.hasText(currentRecording) && registry.isActive(currentRecording)) {
            return Mono.error(LIVE_RECORDING_ACTIVE.exception());
        }
        return policyResolver.resolve(source.getSpec().getStoragePolicy()).flatMap(policy -> {
            var recording = build(source, info, auto, requestedBy, retryOf);
            recording.getSpec().setStoragePolicy(policy.getMetadata().getName());
            return client.create(recording)
                    .flatMap(liveRecorder::launch)
                    .flatMap(launched -> markSourceRecording(sourceName, launched));
        });
    }

    public Mono<LiveRecording> stop(String name, String requestedBy) {
        return recordingStatus.findRequired(name).flatMap(recording -> {
            log.debug("停止录制 [{}] by {}", name, requestedBy);
            return liveRecorder.stop(recording, requestedBy);
        });
    }

    public Mono<LiveRecording> find(String name) {
        return recordingStatus.find(name);
    }

    public Flux<LiveRecording> active() {
        var options = ListOptions.builder()
                .andQuery(in("status.state", ACTIVE_STATES))
                .build();
        return client.listAll(LiveRecording.class, options, defaultSort());
    }

    public Mono<LiveRecordingRetry> retry(String name, String mode, String requestedBy) {
        return recordingStatus.findRequired(name).flatMap(previous -> {
            var state = previous.getStatus() == null ? null : previous.getStatus().getState();
            if (state == null) {
                return Mono.error(LIVE_RECORDING_NOT_READY.exception());
            }
            if (ACTIVE_STATES.contains(state)) {
                return Mono.error(LIVE_RECORDING_ACTIVE.exception());
            }
            if (state != LiveRecordingState.FAILED) {
                return Mono.error(LIVE_RECORDING_NOT_READY.exception());
            }
            var hasLocal = liveRecorder.hasLocalData(previous);
            var effectiveMode = modeOf(mode, hasLocal);
            if (effectiveMode == null) {
                return Mono.error(LIVE_INVALID_ARGUMENT.exception());
            }
            var retried = MODE_UPLOAD.equals(effectiveMode)
                    ? resume(previous, hasLocal)
                    : restart(previous, requestedBy);
            return retried.map(done -> new LiveRecordingRetry(done.getMetadata().getName(), effectiveMode));
        });
    }

    private Mono<LiveRecording> resume(LiveRecording previous, boolean hasLocal) {
        if (!hasLocal) {
            return Mono.error(LIVE_RECORDING_NOT_READY.exception());
        }
        return liveRecorder.resumeUpload(previous).flatMap(this::completeIfUploaded);
    }

    private Mono<LiveRecording> restart(LiveRecording previous, String requestedBy) {
        var sourceName = previous.getSpec().getSourceName();
        return client
                .fetch(LiveSource.class, sourceName)
                .switchIfEmpty(Mono.error(LIVE_SOURCE_NOT_FOUND.exception()))
                .flatMap(source -> {
                    if (source.getSpec() == null
                            || source.getSpec().getPlatform() == null
                            || !source.getSpec().isEnabled()) {
                        return Mono.error(LIVE_INVALID_SPEC.exception());
                    }
                    return roomResolver
                            .resolve(source.getSpec().getPlatform(), source.getSpec().getRoomId(), true)
                            .flatMap(info -> start(
                                    source,
                                    info,
                                    previous.getSpec().isAuto(),
                                    requestedBy,
                                    previous.getMetadata().getName()));
                });
    }

    private String modeOf(String mode, boolean hasLocal) {
        if (!StringUtils.hasText(mode)) {
            return hasLocal ? MODE_UPLOAD : MODE_STREAM;
        }
        var normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (MODE_UPLOAD.equals(normalized) || MODE_STREAM.equals(normalized)) {
            return normalized;
        }
        return null;
    }

    private Mono<LiveRecording> completeIfUploaded(LiveRecording recording) {
        var parts = recording.getStatus().getParts();
        if (parts.isEmpty()) {
            return Mono.just(recording);
        }
        if (parts.stream().anyMatch(part -> part.getState() != PartState.UPLOADED)) {
            return Mono.just(recording);
        }
        var name = recording.getMetadata().getName();
        var firstStartedAt = parts.get(0).getStartedAt();
        var lastEndedAt = parts.get(parts.size() - 1).getEndedAt();
        var durationMillis = firstStartedAt == null || lastEndedAt == null
                ? 0L
                : Math.max(0L, Duration.between(firstStartedAt, lastEndedAt).toMillis());
        var totalBytes = parts.stream().mapToLong(LiveRecording.Part::getSize).sum();
        var segmentCount = parts.stream().mapToInt(LiveRecording.Part::getSegmentCount).sum();
        return recordingStatus
                .mutate(name, status -> {
                    status.setEndedAt(lastEndedAt);
                    status.setDurationMillis(durationMillis);
                    status.setTotalBytes(totalBytes);
                    status.setSegmentCount(segmentCount);
                    status.setPartCount(parts.size());
                    status.setLocalBytes(0L);
                    status.setErrorCode(null);
                    status.setMessage(null);
                })
                .flatMap(updated -> recordingStatus.transition(name, LiveRecordingState.COMPLETED, "续传完成"));
    }

    private Mono<LiveRecording> markSourceRecording(String sourceName, LiveRecording recording) {
        var recordingName = recording.getMetadata().getName();
        if (recording.getStatus().getState() == LiveRecordingState.FAILED) {
            return Mono.just(recording);
        }
        return client.fetch(LiveSource.class, sourceName)
                .flatMap(source -> {
                    if (source.getStatus() == null) {
                        source.setStatus(new LiveSource.Status());
                    }
                    source.getStatus().setCurrentRecording(recordingName);
                    source.getStatus().setState(LiveSourceState.RECORDING);
                    source.getStatus().setMessage(null);
                    source.getStatus().setLastStreamAt(Instant.now());
                    return client.update(source);
                })
                .retryWhen(Retry.backoff(CONFLICT_RETRIES, Duration.ofMillis(CONFLICT_BACKOFF_MILLIS))
                        .maxBackoff(Duration.ofMillis(CONFLICT_MAX_BACKOFF_MILLIS))
                        .jitter(0.0)
                        .filter(OptimisticLockingFailureException.class::isInstance))
                .onErrorResume(error -> {
                    log.warn("回写直播源录制状态失败 [{}]：{}", sourceName, error.getMessage());
                    return Mono.empty();
                })
                .thenReturn(recording);
    }

    private LiveRecording build(
            LiveSource source, LiveRoomInfo info, boolean auto, String requestedBy, String retryOf) {
        var recording = new LiveRecording();
        recording.setMetadata(new Metadata());
        recording.getMetadata().setName(recordingName(source.getMetadata().getName()));
        var spec = new LiveRecording.Spec();
        spec.setSourceName(source.getMetadata().getName());
        spec.setPlatform(source.getSpec().getPlatform());
        spec.setRoomId(info.roomId());
        spec.setRoomTitle(info.title());
        spec.setAnchorName(info.anchorName());
        spec.setFormat(LiveRecordingFormat.TS);
        spec.setStartedAt(Instant.now());
        spec.setAuto(auto);
        spec.setRequestedBy(requestedBy);
        spec.setSegmentSeconds(source.getSpec().getSegmentSeconds());
        spec.setMergeIntervalSeconds(source.getSpec().getMergeIntervalSeconds());
        spec.setRetryOf(retryOf);
        recording.setSpec(spec);
        recording.setStatus(new LiveRecording.Status());
        return recording;
    }

    private String recordingName(String sourceName) {
        var suffix = new StringBuilder(NAME_RANDOM_LENGTH);
        for (int i = 0; i < NAME_RANDOM_LENGTH; i++) {
            suffix.append(NAME_RANDOM_ALPHABET[RANDOM.nextInt(NAME_RANDOM_ALPHABET.length)]);
        }
        return RECORDING_NAME_PREFIX + sourceName + "-" + NAME_TIME_FORMAT.format(Instant.now()) + "-" + suffix;
    }
}
