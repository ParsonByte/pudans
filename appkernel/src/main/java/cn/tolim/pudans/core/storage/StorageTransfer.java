package cn.tolim.pudans.core.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import cn.tolim.pudans.contract.storage.resource.StorageFile;
import cn.tolim.pudans.contract.storage.resource.StorageFileState;
import cn.tolim.pudans.contract.extension.ListResult;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadPartUrl;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageDriverResolver;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.core.storage.driver.StorageSource;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.core.storage.download.DownloadUrl;
import cn.tolim.pudans.core.storage.upload.StorageUploadRequest;
import cn.tolim.pudans.core.storage.upload.StorageWriteRequest;
import cn.tolim.pudans.core.storage.upload.UploadStatus;
import cn.tolim.pudans.core.storage.upload.UploadTicket;
import cn.tolim.pudans.infra.exception.ApiException;
import cn.tolim.pudans.infra.setting.ParameterGroups;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageTransfer {

    private final FileRecords fileRecords;

    private final Uploads uploads;

    private final Downloads downloads;

    private final StorageDriverResolver driverResolver;

    private final ParameterGroups parameterGroups;

    public Mono<UploadTicket> initiateUpload(StorageUploadRequest request) {
        return uploads.initiate(request);
    }

    public Mono<String> issueUploadToken(String fileId) {
        return uploads.issueToken(fileId);
    }

    public Mono<UploadStatus> uploadStatus(String fileId) {
        return uploads.status(fileId);
    }

    public Mono<UploadedPart> putPart(String fileId, int partNumber, long contentLength, Flux<org.springframework.core.io.buffer.DataBuffer> body) {
        return uploads.putPart(fileId, partNumber, contentLength, body);
    }

    public Mono<UploadPartUrl> uploadPartUrl(String fileId, int partNumber) {
        return uploads.partUrl(fileId, partNumber);
    }

    public Mono<StorageFile> completeUpload(String fileId, List<UploadedPart> parts) {
        return uploads.complete(fileId, parts);
    }

    public Mono<Void> abortUpload(String fileId) {
        return uploads.abort(fileId);
    }

    public Mono<StorageFile> upload(StorageWriteRequest request) {
        return uploads.upload(request);
    }

    public Mono<StorageFile> getFile(String fileId) {
        return fileRecords.get(fileId);
    }

    public Mono<ListResult<StorageFile>> listFiles(StorageFileQuery query) {
        return fileRecords.list(query);
    }

    public Mono<StorageFile> trash(String fileId) {
        return fileRecords.get(fileId).flatMap(file -> {
            if (file.getSpec().getState() != StorageFileState.ACTIVE) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT, "storage-file-state-invalid", "只有可读文件可以移入回收站。"));
            }
            return parameterGroups
                    .getStorage()
                    .flatMap(settings -> {
                        var now = Instant.now();
                        file.getSpec().setState(StorageFileState.TRASHED);
                        file.getSpec().setTrashedAt(now);
                        var days = settings.getTrashRetentionDays() == null ? 30 : settings.getTrashRetentionDays();
                        file.getSpec().setTrashExpiresAt(days > 0 ? now.plus(Duration.ofDays(days)) : null);
                        return fileRecords.update(file);
                    });
        });
    }

    public Mono<StorageFile> restore(String fileId) {
        return fileRecords.get(fileId).flatMap(file -> {
            if (file.getSpec().getState() != StorageFileState.TRASHED) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT, "storage-file-state-invalid", "只有回收站中的文件可以恢复。"));
            }
            file.getSpec().setState(StorageFileState.ACTIVE);
            file.getSpec().setTrashedAt(null);
            file.getSpec().setTrashExpiresAt(null);
            return fileRecords.update(file);
        });
    }

    public Mono<Void> purge(String fileId) {
        return fileRecords.fetch(fileId).flatMap(file -> {
            if (file.getSpec().getState() == StorageFileState.UPLOADING) {
                return uploads.abort(fileId);
            }
            if (file.getSpec().getState() != StorageFileState.TRASHED) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT, "storage-file-state-invalid", "只有回收站中的文件可以彻底删除。"));
            }
            // 先按版本删除记录，恢复操作抢先成功时对象不会被删除
            return fileRecords
                    .delete(file)
                    .then(Mono.defer(() -> driverResolver.driver(file.getSpec().getPolicy())))
                    .flatMap(driver -> driver
                            .delete(List.of(file.getSpec().getStorageKey()))
                            .onErrorResume(error -> {
                                log.warn("彻底删除存储对象失败 [{}]", file.getSpec().getStorageKey(), error);
                                return Mono.empty();
                            }));
        });
    }

    public Mono<Void> purgeObjectFirst(String fileId) {
        return fileRecords.fetch(fileId).flatMap(file -> {
            if (file.getSpec().getState() == StorageFileState.UPLOADING) {
                return uploads.abort(fileId);
            }
            // 先删除对象再删除记录，保证重试时还能定位到存储对象
            return driverResolver
                    .driver(file.getSpec().getPolicy())
                    .flatMap(driver -> driver.delete(List.of(file.getSpec().getStorageKey())))
                    .onErrorResume(error -> {
                        log.warn("删除存储对象失败 [{}]", file.getSpec().getStorageKey(), error);
                        return Mono.error(new ApiException(
                                HttpStatus.BAD_GATEWAY, "storage-object-delete-failed", "删除存储对象失败。"));
                    })
                    .then(fileRecords.delete(file));
        });
    }

    public Mono<DownloadUrl> downloadUrl(String fileId, Duration ttl) {
        return downloads.downloadUrl(fileId, ttl);
    }

    public Mono<StorageReadResult> open(String fileId, StorageReadOptions options) {
        return downloads.open(fileId, options);
    }

    public Mono<byte[]> readBytes(String fileId, int maxBytes) {
        return open(fileId, StorageReadOptions.full())
                .flatMap(result -> DataBufferUtils.join(result.body(), maxBytes))
                .map(StorageTransfer::toBytes);
    }

    public Mono<StorageSource> sourceUrl(String fileId, StorageSourceOptions options) {
        return downloads.sourceUrl(fileId, options);
    }

    public void verifyDownload(String token, String fileId) {
        downloads.verify(token, fileId);
    }

    public void verifyUpload(String token, String fileId) {
        uploads.verifyUploadToken(token, fileId);
    }

    public Mono<Void> configureCors(String policyName, List<String> origins) {
        return driverResolver.driver(policyName).flatMap(driver -> {
            if (driver instanceof cn.tolim.pudans.core.storage.driver.CorsConfigurable cors) {
                return cors.configureCors(origins);
            }
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "storage-cors-unsupported", "当前存储不支持配置跨域。"));
        });
    }

    public Mono<StorageTestResult> test(String policyName) {
        return driverResolver.test(policyName);
    }

    private static byte[] toBytes(DataBuffer buffer) {
        try {
            var bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }
}
