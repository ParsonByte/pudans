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
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;
import jakarta.annotation.PreDestroy;
import cn.tolim.pudans.core.storage.driver.StorageIo;
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

    // 创建按录制串行执行的合并线程池
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

    // 登记录制会话并记录合并间隔与起始时间
    public void start(String recordingName, int mergeIntervalSeconds) {
        var session = session(recordingName);
        session.mergeIntervalSeconds = mergeIntervalSeconds;
        session.lastMergeAt = Instant.now();
    }

    // 未失败时按积压、压力或间隔决定是否投递合并
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

    // 排空合并队列并上传，超时由配置决定
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

    // 查询会话是否已进入失败状态
    public boolean isFailed(String recordingName) {
        var session = sessions.get(recordingName);
        return session != null && session.failed;
    }

    // 返回会话失败码，无会话时用合并失败兜底
    public LiveErrorCode failureCode(String recordingName) {
        var session = sessions.get(recordingName);
        return session == null || session.failureCode == null ? LIVE_MERGE_FAILED : session.failureCode;
    }

    // 估算该录制本地分片与成品占用字节
    public long localBytes(String recordingName) {
        return estimate(recordingName).localBytes();
    }

    // 估算该录制尚未上传的成品数量
    public int pendingParts(String recordingName) {
        return estimate(recordingName).parts();
    }

    // 移除录制会话，后续合并请求重新建会话
    public void stop(String recordingName) {
        sessions.remove(recordingName);
    }

    // 关闭合并线程池，丢弃未执行任务
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
    }

    // 反复合并直到无已关闭分片，再上传剩余成品
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

    // 逐个上传未完成成品，任一失败即终止本会话
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

    // 合并一个成品并计时，失败时记录耗时并终止会话
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

    // 将已关闭分片合并为一个成品并登记清单
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
        var size = StorageIo.sizeOf(target);
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
        return RecordingStatus.findPart(updated, index);
    }

    // 顺序复制分片到临时文件，fsync 后原子改名
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
            StorageIo.deleteQuietly(tmp);
            throw new IllegalStateException("合并分片失败：" + name, error);
        }
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException error) {
            StorageIo.deleteQuietly(tmp);
            throw new IllegalStateException("成品文件原子改名失败：" + target, error);
        }
    }

    // 校验成品与策略后上传，失败写入会话失败码
    private void upload(Session session, int index) {
        var name = session.recordingName;
        LiveRecording recording;
        try {
            recording = recordingStatus.findRequired(name).block();
        } catch (RuntimeException error) {
            fail(session, codeOf(error, LIVE_STORAGE_FAILED), "读取录制记录失败：" + error.getMessage());
            return;
        }
        var part = RecordingStatus.findPart(recording, index);
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

    // 刷新成品数、已上传字节与本地占用统计
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

    // 合并成功后删除已消费的分片文件
    private void deleteSegments(String name, java.util.List<LiveSegment> closed) {
        for (var segment : closed) {
            try {
                Files.deleteIfExists(workspace.segmentPath(name, segment.index()));
            } catch (IOException error) {
                log.warn("删除已合并分片失败 [{}#{}]：{}", name, segment.index(), error.getMessage());
            }
        }
    }

    // 首次失败时固定失败码，后续失败不覆盖
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

    // 取或创建录制会话，保证同一录制串行
    private Session session(String recordingName) {
        return sessions.computeIfAbsent(recordingName, name -> new Session(name, executor));
    }

    // 间隔为零表示按需合并，否则按秒数判断
    private boolean intervalElapsed(Session session) {
        if (session.mergeIntervalSeconds <= 0) {
            return false;
        }
        return Duration.between(session.lastMergeAt, Instant.now()).toSeconds() >= session.mergeIntervalSeconds;
    }

    // 统计本地分片与成品文件的大小与数量
    private Estimate estimate(String recordingName) {
        long bytes = 0;
        var segments = workspace.listSegmentFiles(recordingName);
        for (var segment : segments) {
            bytes += StorageIo.sizeOf(segment);
        }
        var parts = workspace.listPartFiles(recordingName);
        for (var part : parts) {
            bytes += StorageIo.sizeOf(part);
        }
        return new Estimate(bytes, segments.size(), parts.size());
    }

    // 取记录与现存文件中的最大索引加一
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

    // 从成品文件名解析索引，非法名称按零处理
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

    // 取平台名用于指标标签，空平台返回 null
    private static String platformName(LiveRecording recording) {
        var platform = recording.getSpec().getPlatform();
        return platform == null ? null : platform.name();
    }

    // 优先复用异常携带的直播错误码，否则用兜底码
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

        // 会话持有录制名与串行队列，状态跨任务共享
        private Session(String recordingName, ExecutorService executor) {
            this.recordingName = recordingName;
            this.queue = new SerialQueue(executor);
        }
    }

    private static final class SerialQueue {

        private final Deque<Runnable> tasks = new ArrayDeque<>();

        private final ExecutorService executor;

        private boolean running;

        // 串行队列复用合并线程池，按录制保证顺序
        private SerialQueue(ExecutorService executor) {
            this.executor = executor;
        }

        // 追加任务并在空闲时唤醒消费循环
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

        // 逐条执行队列任务，单任务失败不中断后续
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

        // 合并线程按序号命名并设为守护线程
        @Override
        public Thread newThread(Runnable runnable) {
            var thread = new Thread(runnable, "live-merger-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        }
    }
}
