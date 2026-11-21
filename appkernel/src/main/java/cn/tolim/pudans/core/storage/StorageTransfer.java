package cn.tolim.pudans.core.storage;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import cn.tolim.pudans.contract.storage.StorageFile;
import cn.tolim.pudans.contract.storage.StorageFileState;
import cn.tolim.pudans.extension.contract.ListResult;
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

    // 创建上传会话并返回客户端上传凭证。
    public Mono<UploadTicket> initiateUpload(StorageUploadRequest request) {
        return uploads.initiate(request);
    }

    // 为仍有效的上传会话续签上传令牌。
    public Mono<String> issueUploadToken(String fileId) {
        return uploads.issueToken(fileId);
    }

    // 查询上传会话已接收的分片与状态。
    public Mono<UploadStatus> uploadStatus(String fileId) {
        return uploads.status(fileId);
    }

    // 将单个分片写入上传会话。
    public Mono<UploadedPart> putPart(String fileId, int partNumber, long contentLength, Flux<org.springframework.core.io.buffer.DataBuffer> body) {
        return uploads.putPart(fileId, partNumber, contentLength, body);
    }

    // 生成单个分片的预签名上传地址。
    public Mono<UploadPartUrl> uploadPartUrl(String fileId, int partNumber) {
        return uploads.partUrl(fileId, partNumber);
    }

    // 合并分片并将文件置为可读状态。
    public Mono<StorageFile> completeUpload(String fileId, List<UploadedPart> parts) {
        return uploads.complete(fileId, parts);
    }

    // 终止上传会话并清理中间数据。
    public Mono<Void> abortUpload(String fileId) {
        return uploads.abort(fileId);
    }

    // 一次性写入内容并创建可读文件记录。
    public Mono<StorageFile> upload(StorageWriteRequest request) {
        return uploads.upload(request);
    }

    // 按标识读取文件记录。
    public Mono<StorageFile> getFile(String fileId) {
        return fileRecords.get(fileId);
    }

    // 按查询条件分页列出文件记录。
    public Mono<ListResult<StorageFile>> listFiles(StorageFileQuery query) {
        return fileRecords.list(query);
    }

    // 将可读文件移入回收站并设置清理时间。
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

    // 将回收站文件恢复为可读状态。
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

    // 彻底删除回收站文件及其存储对象。
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

    // 先删对象再删记录，便于失败后重试。
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

    // 生成带令牌与有效期的下载地址。
    public Mono<DownloadUrl> downloadUrl(String fileId, Duration ttl) {
        return downloads.downloadUrl(fileId, ttl);
    }

    // 打开文件内容供调用方读取。
    public Mono<StorageReadResult> open(String fileId, StorageReadOptions options) {
        return downloads.open(fileId, options);
    }

    // 读取文件内容并限制最大字节数。
    public Mono<byte[]> readBytes(String fileId, int maxBytes) {
        return open(fileId, StorageReadOptions.full())
                .flatMap(result -> DataBufferUtils.join(result.body(), maxBytes))
                .map(StorageTransfer::toBytes);
    }

    // 获取预签名直链，不支持时返回空。
    public Mono<StorageSource> sourceUrl(String fileId, StorageSourceOptions options) {
        return downloads.sourceUrl(fileId, options);
    }

    // 校验下载令牌是否匹配目标文件。
    public void verifyDownload(String token, String fileId) {
        downloads.verify(token, fileId);
    }

    // 校验上传令牌是否匹配目标文件。
    public void verifyUpload(String token, String fileId) {
        uploads.verifyUploadToken(token, fileId);
    }

    // 为支持跨域的驱动设置允许来源。
    public Mono<Void> configureCors(String policyName, List<String> origins) {
        return driverResolver.driver(policyName).flatMap(driver -> {
            if (driver instanceof cn.tolim.pudans.core.storage.driver.CorsConfigurable cors) {
                return cors.configureCors(origins);
            }
            return Mono.error(new ApiException(
                    HttpStatus.BAD_REQUEST, "storage-cors-unsupported", "当前存储不支持配置跨域。"));
        });
    }

    // 测试指定策略的连通性。
    public Mono<StorageTestResult> test(String policyName) {
        return driverResolver.test(policyName);
    }

    // 读取缓冲区并确保其被释放。
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
