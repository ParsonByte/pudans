package cn.tolim.pudans.core.live.recorder;

import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STORAGE_FAILED;
import static cn.tolim.pudans.contract.live.dto.LiveErrorCode.LIVE_STORAGE_POLICY_INVALID;

import java.io.IOException;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.live.LiveRecording;
import cn.tolim.pudans.contract.live.PartState;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.core.live.recording.RecordingStatus;
import cn.tolim.pudans.core.storage.StoragePolicyValidator;
import cn.tolim.pudans.core.storage.StorageTransfer;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageIo;
import cn.tolim.pudans.core.storage.upload.StorageUploadRequest;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.core.storage.upload.UploadTicket;
import cn.tolim.pudans.core.live.monitor.LiveMetrics;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.properties.LiveProperties;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;

// 把合并后的本地成品流式上传到对象存储，本地文件只是缓存
@Slf4j
@Component
@RequiredArgsConstructor
public class LivePartUploader {

    private static final String MODULE = "live";

    private static final String OWNER = "live-recorder";

    private static final String CONTENT_TYPE = "video/mp2t";

    private static final String ORIGIN_PREFIX = "live-recordings/";

    private static final Duration RETRY_INITIAL_BACKOFF = Duration.ofSeconds(1);

    private static final Duration RETRY_MAX_BACKOFF = Duration.ofSeconds(4);

    private final StorageTransfer storageTransfer;

    private final StorageDriverResolver driverResolver;

    private final StoragePolicyValidator storagePolicyValidator;

    private final RecordingStatus recordingStatus;

    private final LiveProperties properties;

    private final LiveMetrics metrics;

    // 标记上传中，失败写入状态并可重试整个上传
    public Mono<LiveRecording.Part> upload(
            LiveRecording recording, LiveRecording.Part part, Path file, StoragePolicy policy) {
        if (!LiveStoragePolicyResolver.isRecordable(policy)) {
            metrics.storagePolicyInvalid();
            log.warn("录制成品上传前策略校验失败 [{}]", recording.getMetadata().getName());
            return Mono.error(LIVE_STORAGE_POLICY_INVALID.exception());
        }
        var name = recording.getMetadata().getName();
        var index = part.getIndex();
        return recordingStatus
                .mutatePart(name, index, uploading -> {
                    uploading.setState(PartState.UPLOADING);
                    uploading.setErrorCode(null);
                    uploading.setMessage(null);
                })
                .flatMap(updated -> Mono.fromCallable(() -> Files.size(file))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(size -> transfer(recording, part, file, policy, size)
                                .retryWhen(uploadRetry())
                                .onErrorResume(error -> uploadFailed(name, index, error)
                                        .then(Mono.error(error)))
                                .flatMap(stored -> markUploaded(name, index, stored, file, size))));
    }

    // 小文件单次上传，大文件要求驱动支持分片上传
    private Mono<StorageFile> transfer(
            LiveRecording recording, LiveRecording.Part part, Path file, StoragePolicy policy, long size) {
        var chunkSize = storagePolicyValidator.chunkSize(policy);
        if (size <= chunkSize) {
            return storageTransfer.upload(writeRequest(recording, part, file, policy, size));
        }
        return driverResolver.driver(policy).flatMap(driver -> {
            if (!driver.capabilities().contains(StorageCapability.MULTIPART_UPLOAD)) {
                metrics.storagePolicyInvalid();
                log.warn("存储策略 [{}] 不支持原生分片上传，拒绝存放录制成品", policy.getMetadata().getName());
                return Mono.error(LIVE_STORAGE_POLICY_INVALID.exception());
            }
            return multipartUpload(recording, part, file, policy, size);
        });
    }

    // 初始化分片上传，失败时尽力中止已创建的上传
    private Mono<StorageFile> multipartUpload(
            LiveRecording recording, LiveRecording.Part part, Path file, StoragePolicy policy, long size) {
        return storageTransfer.initiateUpload(uploadRequest(recording, part, policy, size)).flatMap(ticket -> uploadParts(
                        ticket, file, size)
                .flatMap(parts -> storageTransfer.completeUpload(ticket.fileId(), parts))
                .onErrorResume(error -> storageTransfer
                        .abortUpload(ticket.fileId())
                        .onErrorResume(abortError -> Mono.empty())
                        .then(Mono.error(error))));
    }

    // 每个分片按 ticket 的分片大小流式读取，JVM 堆里最多只有一个分片缓冲
    private Mono<List<UploadedPart>> uploadParts(UploadTicket ticket, Path file, long size) {
        return Flux.range(1, ticket.partCount())
                .concatMap(number -> {
                    var offset = (long) (number - 1) * ticket.chunkSize();
                    var length = Math.min(ticket.chunkSize(), size - offset);
                    var body = Flux.defer(() -> readSlice(file, offset, length));
                    return storageTransfer.putPart(ticket.fileId(), number, length, body);
                })
                .collectList();
    }

    // 用定位后的 FileChannel 读取，交给 StorageIo 生成 64KB 的 DataBuffer
    private static Flux<DataBuffer> readSlice(Path file, long offset, long length) {
        FileChannel channel = null;
        try {
            channel = FileChannel.open(file, StandardOpenOption.READ);
            channel.position(offset);
            return StorageIo.read(Channels.newInputStream(channel), length);
        } catch (IOException error) {
            if (channel != null) {
                closeQuietly(channel);
            }
            return Flux.error(error);
        }
    }

    // 上传成功后标记成品并删除本地缓存文件
    private Mono<LiveRecording.Part> markUploaded(
            String name, int index, StorageFile stored, Path file, long size) {
        var storageFile = stored.getMetadata().getName();
        return recordingStatus
                .mutatePart(name, index, uploaded -> {
                    uploaded.setState(PartState.UPLOADED);
                    uploaded.setStorageFile(storageFile);
                    uploaded.setSize(size);
                    uploaded.setErrorCode(null);
                    uploaded.setMessage(null);
                })
                .flatMap(updated -> Mono.fromRunnable(() -> deleteLocal(file))
                        .subscribeOn(Schedulers.boundedElastic())
                        .thenReturn(RecordingStatus.findPart(updated, index)));
    }

    // 记录成品失败状态并把错误继续抛给调用方
    private Mono<LiveRecording.Part> uploadFailed(String name, int index, Throwable error) {
        metrics.partUploadFail();
        var code = error instanceof ApiException apiException
                ? apiException.getCode()
                : LIVE_STORAGE_FAILED.name();
        log.warn("录制成品上传失败 [{}#{}]：{}", name, index, error.getMessage());
        var failure = error instanceof ApiException ? error : LIVE_STORAGE_FAILED.exception();
        return recordingStatus
                .mutatePart(name, index, failed -> {
                    failed.setState(PartState.FAILED);
                    failed.setErrorCode(code);
                    failed.setMessage(message(error));
                })
                .then(Mono.error(failure));
    }

    // 构造单次上传请求，流式读取本地成品
    private StorageWriteRequest writeRequest(
            LiveRecording recording, LiveRecording.Part part, Path file, StoragePolicy policy, long size) {
        return new StorageWriteRequest(
                policy.getMetadata().getName(),
                OWNER,
                MODULE,
                originPath(recording, part),
                originalName(part),
                CONTENT_TYPE,
                size,
                attributes(recording, part),
                Flux.defer(() -> readSlice(file, 0, size)),
                false);
    }

    // 构造分片上传的初始化请求
    private StorageUploadRequest uploadRequest(
            LiveRecording recording, LiveRecording.Part part, StoragePolicy policy, long size) {
        return new StorageUploadRequest(
                policy.getMetadata().getName(),
                OWNER,
                MODULE,
                originPath(recording, part),
                originalName(part),
                CONTENT_TYPE,
                size,
                attributes(recording, part),
                null);
    }

    // 对象存储来源路径按录制名与成品名生成
    private static String originPath(LiveRecording recording, LiveRecording.Part part) {
        return ORIGIN_PREFIX + recording.getMetadata().getName() + "/" + fileName(part);
    }

    // 原始文件名优先用清单文件名，缺失时回退
    private static String originalName(LiveRecording.Part part) {
        return StringUtils.hasText(part.getFilename()) ? part.getFilename() : fileName(part);
    }

    // 成品固定命名格式，索引补零便于排序
    private static String fileName(LiveRecording.Part part) {
        return String.format("part-%05d.ts", part.getIndex());
    }

    // 附加录制、源与成品元数据供对象检索
    private static Map<String, String> attributes(LiveRecording recording, LiveRecording.Part part) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("recording", recording.getMetadata().getName());
        var spec = recording.getSpec();
        if (StringUtils.hasText(spec.getSourceName())) {
            attributes.put("sourceName", spec.getSourceName());
        }
        if (StringUtils.hasText(spec.getRoomId())) {
            attributes.put("roomId", spec.getRoomId());
        }
        attributes.put("part", String.valueOf(part.getIndex()));
        attributes.put("segmentCount", String.valueOf(part.getSegmentCount()));
        return attributes;
    }

    // 上传重试按配置次数退避，策略错误不重试
    private Retry uploadRetry() {
        var retries = Math.max(properties.getRecorder().getUploadRetries(), 0);
        return Retry.backoff(retries, RETRY_INITIAL_BACKOFF)
                .maxBackoff(RETRY_MAX_BACKOFF)
                .jitter(0)
                .filter(error -> !isPolicyInvalid(error));
    }

    // 策略非法属于配置问题，重复上传没有意义
    private static boolean isPolicyInvalid(Throwable error) {
        return error instanceof ApiException apiException
                && LIVE_STORAGE_POLICY_INVALID.name().equals(apiException.getCode());
    }

    // 错误信息缺失时使用通用存储失败文案
    private static String message(Throwable error) {
        return error.getMessage() == null ? LIVE_STORAGE_FAILED.message() : error.getMessage();
    }

    // 本地成品删除失败只告警，上传结果已经写入录制状态
    private static void deleteLocal(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException error) {
            log.warn("删除本地录制成品失败 [{}]：{}", file, error.getMessage());
        }
    }

    // 关闭通道失败无需处理，只释放资源
    private static void closeQuietly(FileChannel channel) {
        try {
            channel.close();
        } catch (IOException ignored) {
        }
    }
}