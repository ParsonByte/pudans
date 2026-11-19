package cn.tolim.pudans.core.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import cn.tolim.pudans.contract.core.extension.storage.StorageFile;
import cn.tolim.pudans.contract.core.extension.storage.StorageFileState;
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
import cn.tolim.pudans.infra.setting.ParameterSettingGroupService;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class StorageService {

    private final StorageFileService fileService;

    private final StorageUploadService uploadService;

    private final StorageDownloadService downloadService;

    private final StorageDriverResolver driverResolver;

    private final ParameterSettingGroupService parameterSettingGroupService;

    public Mono<UploadTicket> initiateUpload(StorageUploadRequest request) {
        return uploadService.initiate(request);
    }

    public Mono<String> issueUploadToken(String fileId) {
        return uploadService.issueToken(fileId);
    }

    public Mono<UploadStatus> uploadStatus(String fileId) {
        return uploadService.status(fileId);
    }

    public Mono<UploadedPart> putPart(String fileId, int partNumber, long contentLength, Flux<org.springframework.core.io.buffer.DataBuffer> body) {
        return uploadService.putPart(fileId, partNumber, contentLength, body);
    }

    public Mono<UploadPartUrl> uploadPartUrl(String fileId, int partNumber) {
        return uploadService.partUrl(fileId, partNumber);
    }

    public Mono<StorageFile> completeUpload(String fileId, List<UploadedPart> parts) {
        return uploadService.complete(fileId, parts);
    }

    public Mono<Void> abortUpload(String fileId) {
        return uploadService.abort(fileId);
    }

    public Mono<StorageFile> upload(StorageWriteRequest request) {
        return uploadService.upload(request);
    }

    public Mono<StorageFile> getFile(String fileId) {
        return fileService.get(fileId);
    }

    public Mono<ListResult<StorageFile>> listFiles(StorageFileQuery query) {
        return fileService.list(query);
    }

    public Mono<StorageFile> trash(String fileId) {
        return fileService.get(fileId).flatMap(file -> {
            if (file.getSpec().getState() != StorageFileState.ACTIVE) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT, "storage-file-state-invalid", "只有可读文件可以移入回收站。"));
            }
            return parameterSettingGroupService
                    .getStorage()
                    .flatMap(settings -> {
                        var now = Instant.now();
                        file.getSpec().setState(StorageFileState.TRASHED);
                        file.getSpec().setTrashedAt(now);
                        var days = settings.getTrashRetentionDays() == null ? 30 : settings.getTrashRetentionDays();
                        file.getSpec().setTrashExpiresAt(days > 0 ? now.plus(Duration.ofDays(days)) : null);
                        return fileService.update(file);
                    });
        });
    }

    public Mono<StorageFile> restore(String fileId) {
        return fileService.get(fileId).flatMap(file -> {
            if (file.getSpec().getState() != StorageFileState.TRASHED) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT, "storage-file-state-invalid", "只有回收站中的文件可以恢复。"));
            }
            file.getSpec().setState(StorageFileState.ACTIVE);
            file.getSpec().setTrashedAt(null);
            file.getSpec().setTrashExpiresAt(null);
            return fileService.update(file);
        });
    }

    public Mono<Void> purge(String fileId) {
        return fileService.fetch(fileId).flatMap(file -> {
            if (file.getSpec().getState() == StorageFileState.UPLOADING) {
                return uploadService.abort(fileId);
            }
            if (file.getSpec().getState() != StorageFileState.TRASHED) {
                return Mono.error(new ApiException(
                        HttpStatus.CONFLICT, "storage-file-state-invalid", "只有回收站中的文件可以彻底删除。"));
            }
            return driverResolver
                    .driver(file.getSpec().getPolicy())
                    .flatMap(driver -> driver.delete(List.of(file.getSpec().getStorageKey())))
                    .then(Mono.defer(() -> fileService.delete(file)));
        });
    }

    public Mono<DownloadUrl> downloadUrl(String fileId, Duration ttl) {
        return downloadService.downloadUrl(fileId, ttl);
    }

    public Mono<StorageReadResult> open(String fileId, StorageReadOptions options) {
        return downloadService.open(fileId, options);
    }

    public Mono<byte[]> readBytes(String fileId, int maxBytes) {
        return open(fileId, StorageReadOptions.full())
                .flatMap(result -> DataBufferUtils.join(result.body(), maxBytes))
                .map(StorageService::toBytes);
    }

    public Mono<StorageSource> sourceUrl(String fileId, StorageSourceOptions options) {
        return downloadService.sourceUrl(fileId, options);
    }

    public void verifyDownload(String token, String fileId) {
        downloadService.verify(token, fileId);
    }

    public void verifyUpload(String token, String fileId) {
        uploadService.verifyUploadToken(token, fileId);
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
