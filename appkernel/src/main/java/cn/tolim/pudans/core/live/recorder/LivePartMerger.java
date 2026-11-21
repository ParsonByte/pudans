package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_MERGE_FAILED;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STORAGE_BACKLOG;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STORAGE_FAILED;

import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.live.dto.LiveErrorCode;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.infra.exception.ApiException;
import cn.tolim.pudans.core.infra.properties.LiveProperties;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

// 合并与上传按录制串行执行，录制线程只负责关闭分片并投递请求
@Slf4j
@Component
public class LivePartMerger {

    private static final int MAX_DRAIN_ROUNDS = 64;

    private final LiveRecordingWorkspace workspace;

    private final LiveSegmentManifest segmentManifest;

    private final LivePartManifest partManifest;

    private final RecordingStatus recordingStatus;

    private final LivePartUploader uploader;

    private final LiveStoragePolicyResolver policyResolver;

    private final LiveProperties properties;

    private final LiveMetrics metrics;

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    private final ExecutorService executor;

    public LivePartMerger(
            LiveRecordingWorkspace workspace,
            LiveSegmentManifest segmentManifest,
            LivePartManifest partManifest,
            RecordingStatus recordingStatus,
            LivePartUploader uploader,
            LiveStoragePolicyResolver policyResolver,
            LiveProperties properties,
            LiveMetrics metrics) {
        this.workspace = workspace;
        this.segmentManifest = segmentManifest;
        this.partManifest = partManifest;
        this.recordingStatus = recordingStatus;
        this.uploader = uploader;
        this.policyResolver = policyResolver;
        this.properties = properties;
        this.metrics = metrics;
        this.executor = Executors.newCachedThreadPool(new MergerThreadFactory());
    }

    public void start(String recordingName, int mergeIntervalSeconds) {
        var session = session(recordingName);
        session.mergeIntervalSeconds = mergeIntervalSeconds;
        session.lastMergeAt = Instant.now();
    }

    public void requestMerge(String recordingName, boolean force) {
        var session = session(recordingName);
        if (session.failed) {
            return;
        }
        var estimate = estimate(recordingName);
        if (!force && estimate.parts() >= properties.getRecorder().getMerge().getMaxPendingParts()) {
            fail(session, LIVE_STORAGE_BACKLOG, "待上传成品积压");
            return;
        }
        var pressure = estimate.localBytes() >= properties.getRecorder().getMerge().getMaxLocalBytes().toBytes()
                || estimate.segments() >= properties.getRecorder().getMerge().getMaxLocalSegments();
        if (!force && !pressure && !intervalElapsed(session)) {
            return;
        }
        session.lastMergeAt = Instant.now();
        session.queue.execute(() -> runMerge(session));
    }

    public Mono<Void> finalizeRecording(String recordingName) {
        var session = session(recordingName);
        var done = new CompletableFuture<Void>();
        session.queue.execute(() -> {
            try {
                drain(session);
                done.complete(null);
            } catch (RuntimeException error) {
                done.completeExceptionally(error);
            }
        });
        return Mono.fromFuture(done).timeout(properties.getRecorder().getFinalizeTimeout());
    }

    public boolean isFailed(String recordingName) {
        var session = sessions.get(recordingName);
        return session != null && session.failed;
    }

    public LiveErrorCode failureCode(String recordingName) {
        var session = sessions.get(recordingName);
        return session == null || session.failureCode == null ? LIVE_MERGE_FAILED : session.failureCode;
    }

    public long localBytes(String recordingName) {
        return estimate(recordingName).localBytes();
    }

    public int pendingParts(String recordingName) {
        return estimate(recordingName).parts();
    }

    public void stop(String recordingName) {
        sessions.remove(recordingName);
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    private void drain(Session session) {
        for (var round = 0; round < MAX_DRAIN_ROUNDS; round++) {
            if (session.failed) {
                return;
            }
            if (segmentManifest.closedSegments(session.recordingName).isEmpty()) {
                uploadPending(session);
                if (!session.failed) {
                    refreshTotals(session.recordingName);
                }
                return;
            }
            runMerge(session);
        }
        log.warn("录制收尾合并轮次超过上限 [{}]", session.recordingName);
    }

    private void uploadPending(Session session) {
        var name = session.recordingName;
        LiveRecording recording;
        try {
            recording = recordingStatus.findRequired(name).block();
        } catch (RuntimeException error) {
            fail(session, codeOf(error, LIVE_MERGE_FAILED), "读取录制记录失败：" + error.getMessage());
            return;
        }
        for (var part : recording.getStatus().getParts()) {
            if (part.getState() == PartState.UPLOADED) {
                continue;
            }
            if (!Files.isRegularFile(workspace.partPath(name, part.getIndex()))) {
                fail(session, LIVE_MERGE_FAILED, "成品文件不存在：" + part.getFilename());
                return;
            }
            upload(session, part.getIndex());
            if (session.failed) {
                return;
            }
        }
    }

    private void runMerge(Session session) {
        if (session.failed) {
            return;
        }
        LiveRecording recording;
        try {
            recording = recordingStatus.findRequired(session.recordingName).block();
        } catch (RuntimeException error) {
            fail(session, codeOf(error, LIVE_MERGE_FAILED), "读取录制记录失败：" + error.getMessage());
            return;
        }
        var startedAt = Instant.now();
        try {
            var part = merge(recording);
            if (part != null) {
                refreshTotals(session.recordingName);
                upload(session, part.getIndex());
            }
            metrics.merge(Duration.between(startedAt, Instant.now()), true);
        } catch (RuntimeException error) {
            metrics.merge(Duration.between(startedAt, Instant.now()), false);
            metrics.mergeFail();
            fail(session, codeOf(error, LIVE_MERGE_FAILED), "合并或上传录制成品失败：" + error.getMessage());
        }
    }

    private LiveRecording.Part merge(LiveRecording recording) {
        var name = recording.getMetadata().getName();
        var closed = segmentManifest.closedSegments(name);
        if (closed.isEmpty()) {
            return null;
        }
        var index = nextPartIndex(recording);
        var part = new LiveRecording.Part();
        part.setIndex(index);
        part.setFilename(LiveRecordingWorkspace.partFilename(index));
        part.setState(PartState.MERGING);
        part.setStartedAt(closed.getFirst().startedAt());
        part.setEndedAt(closed.getLast().endedAt());
        part.setSegmentCount(closed.size());
        recordingStatus.addPart(name, part).block();
        partManifest.append(name, part);

        var target = workspace.partPath(name, index);
        var tmp = workspace.partTempPath(name, index);
        copySegments(name, closed, tmp, target);
        var size = sizeOf(target);
        part.setSize(size);
        part.setDurationMillis(closed.stream().mapToLong(LiveSegment::durationMillis).sum());
        var updated = recordingStatus
                .mutatePart(name, index, stored -> {
                    stored.setSize(part.getSize());
                    stored.setDurationMillis(part.getDurationMillis());
                })
                .block();
        partManifest.append(name, part);
        deleteSegments(name, closed);
        return findPart(updated, index);
    }

    private void copySegments(String name, java.util.List<LiveSegment> closed, Path tmp, Path target) {
        try {
            Files.deleteIfExists(tmp);
        } catch (IOException error) {
            throw new IllegalStateException("清理成品临时文件失败：" + tmp, error);
        }
        try (var out = FileChannel.open(tmp, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            for (var segment : closed) {
                try (var in = FileChannel.open(workspace.segmentPath(name, segment.index()), StandardOpenOption.READ)) {
                    long position = 0;
                    while (position < in.size()) {
                        position += in.transferTo(position, in.size() - position, out);
                    }
                }
            }
            out.force(true);
        } catch (IOException error) {
            deleteQuietly(tmp);
            throw new IllegalStateException("合并分片失败：" + name, error);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            deleteQuietly(tmp);
            throw new IllegalStateException("成品文件原子改名失败：" + target, error);
        }
    }

    private void upload(Session session, int index) {
        var name = session.recordingName;
        LiveRecording recording;
        try {
            recording = recordingStatus.findRequired(name).block();
        } catch (RuntimeException error) {
            fail(session, codeOf(error, LIVE_STORAGE_FAILED), "读取录制记录失败：" + error.getMessage());
            return;
        }
        var part = findPart(recording, index);
        if (part == null) {
            fail(session, LIVE_MERGE_FAILED, "录制成品索引不存在：" + index);
            return;
        }
        if (part.getState() == PartState.UPLOADED) {
            return;
        }
        var file = workspace.partPath(name, index);
        if (!Files.isRegularFile(file)) {
            fail(session, LIVE_MERGE_FAILED, "成品文件不存在：" + part.getFilename());
            return;
        }
        StoragePolicy policy;
        try {
            policy = policyResolver.resolve(recording.getSpec().getStoragePolicy()).block();
        } catch (RuntimeException error) {
            fail(session, codeOf(error, LIVE_STORAGE_FAILED), "录制成品上传策略不可用：" + error.getMessage());
            return;
        }
        try {
            var uploaded = uploader.upload(recording, part, file, policy).block();
            if (uploaded != null) {
                metrics.recordingBytes(platformName(recording), uploaded.getSize());
            }
            refreshTotals(name);
        } catch (RuntimeException error) {
            fail(session, codeOf(error, LIVE_STORAGE_FAILED), "录制成品上传失败：" + error.getMessage());
        }
    }

    private void refreshTotals(String name) {
        try {
            recordingStatus
                    .mutate(name, status -> {
                        var parts = status.getParts();
                        status.setPartCount(parts.size());
                        status.setTotalBytes(parts.stream()
                                .filter(part -> part.getState() == PartState.UPLOADED)
                                .mapToLong(LiveRecording.Part::getSize)
                                .sum());
                        status.setLocalBytes(localBytes(name));
                    })
                    .block();
        } catch (RuntimeException error) {
            log.warn("刷新录制成品统计失败 [{}]：{}", name, error.getMessage());
        }
    }

    private void deleteSegments(String name, java.util.List<LiveSegment> closed) {
        for (var segment : closed) {
            try {
                Files.deleteIfExists(workspace.segmentPath(name, segment.index()));
            } catch (IOException error) {
                log.warn("删除已合并分片失败 [{}#{}]：{}", name, segment.index(), error.getMessage());
            }
        }
    }

    private void fail(Session session, LiveErrorCode code, String message) {
        if (session.failed) {
            return;
        }
        session.failed = true;
        session.failureCode = code;
        log.warn("录制进入失败状态 [{}] {}：{}", session.recordingName, code.name(), message);
        try {
            recordingStatus.fail(session.recordingName, code, message).block();
        } catch (RuntimeException error) {
            log.warn("写入录制失败状态失败 [{}]：{}", session.recordingName, error.getMessage());
        }
    }

    private Session session(String recordingName) {
        return sessions.computeIfAbsent(recordingName, name -> new Session(name, executor));
    }

    private boolean intervalElapsed(Session session) {
        if (session.mergeIntervalSeconds <= 0) {
            return false;
        }
        return Duration.between(session.lastMergeAt, Instant.now()).toSeconds() >= session.mergeIntervalSeconds;
    }

    private Estimate estimate(String recordingName) {
        long bytes = 0;
        var segments = workspace.listSegmentFiles(recordingName);
        for (var segment : segments) {
            bytes += sizeOf(segment);
        }
        var parts = workspace.listPartFiles(recordingName);
        for (var part : parts) {
            bytes += sizeOf(part);
        }
        return new Estimate(bytes, segments.size(), parts.size());
    }

    private int nextPartIndex(LiveRecording recording) {
        var index = 0;
        for (var part : recording.getStatus().getParts()) {
            index = Math.max(index, part.getIndex());
        }
        for (var file : workspace.listPartFiles(recording.getMetadata().getName())) {
            index = Math.max(index, indexOf(file));
        }
        return index + 1;
    }

    private static int indexOf(Path file) {
        var filename = file.getFileName().toString();
        var dash = filename.indexOf('-');
        var dot = filename.lastIndexOf('.');
        if (dash < 0 || dot <= dash + 1) {
            return 0;
        }
        try {
            return Integer.parseInt(filename.substring(dash + 1, dot));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static LiveRecording.Part findPart(LiveRecording recording, int index) {
        for (var part : recording.getStatus().getParts()) {
            if (part.getIndex() == index) {
                return part;
            }
        }
        return null;
    }

    private static String platformName(LiveRecording recording) {
        var platform = recording.getSpec().getPlatform();
        return platform == null ? null : platform.name();
    }

    private static long sizeOf(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0;
        } catch (IOException error) {
            return 0;
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException error) {
            log.warn("删除临时文件失败 [{}]：{}", path, error.getMessage());
        }
    }

    private static LiveErrorCode codeOf(Throwable error, LiveErrorCode fallback) {
        if (error instanceof ApiException apiException && apiException.getCode() != null) {
            try {
                return LiveErrorCode.valueOf(apiException.getCode());
            } catch (IllegalArgumentException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private record Estimate(long localBytes, int segments, int parts) {}

    private static final class Session {

        private final String recordingName;

        private final SerialQueue queue;

        private volatile int mergeIntervalSeconds;

        private volatile Instant lastMergeAt = Instant.now();

        private volatile boolean failed;

        private volatile LiveErrorCode failureCode;

        private Session(String recordingName, ExecutorService executor) {
            this.recordingName = recordingName;
            this.queue = new SerialQueue(executor);
        }
    }

    private static final class SerialQueue {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        private final ExecutorService executor;

        private boolean running;

        private SerialQueue(ExecutorService executor) {
            this.executor = executor;
        }

        private void execute(Runnable task) {
            synchronized (this) {
                tasks.add(task);
                if (running) {
                    return;
                }
                running = true;
            }
            executor.execute(this::drain);
        }

        private void drain() {
            while (true) {
                Runnable task;
                synchronized (this) {
                    task = tasks.poll();
                    if (task == null) {
                        running = false;
                        return;
                    }
                }
                try {
                    task.run();
                } catch (RuntimeException error) {
                    log.warn("录制合并队列任务失败：{}", error.getMessage());
                }
            }
        }
    }

    private static final class MergerThreadFactory implements ThreadFactory {

        private final AtomicInteger index = new AtomicInteger();

        @Override
        public Thread newThread(Runnable runnable) {
            var thread = new Thread(runnable, "live-merger-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
