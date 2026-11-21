package cn.tolim.pudans.core.storage.driver.obs;

import cn.tolim.pudans.core.storage.driver.StorageDriverErrors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.CorsConfigurable;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.NativeMultipartUpload;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.StorageMultipartRequest;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadPartUrl;
import cn.tolim.pudans.core.storage.driver.MultipartStorageDriver.UploadedPart;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageIo;
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StoragePutRequest;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.core.storage.driver.StorageSource;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.infra.exception.ApiException;
import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.AbortMultipartUploadRequest;
import com.obs.services.model.BucketCors;
import com.obs.services.model.BucketCorsRule;
import com.obs.services.model.CompleteMultipartUploadRequest;
import com.obs.services.model.GetObjectRequest;
import com.obs.services.model.InitiateMultipartUploadRequest;
import com.obs.services.model.ListPartsRequest;
import com.obs.services.model.PartEtag;
import com.obs.services.model.PutObjectRequest;
import com.obs.services.model.SetBucketCorsRequest;
import com.obs.services.model.UploadPartRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class ObsStorageDriver implements StorageDriver, MultipartStorageDriver, CorsConfigurable {

    private static final long DEFAULT_CHUNK_SIZE = 8L * 1024L * 1024L;

    private final StoragePolicy policy;

    private final ObsClient client;

    private final Path tempDirectory;

    // 保存策略、OBS 客户端与临时目录。
    public ObsStorageDriver(StoragePolicy policy, ObsClient client, Path tempDirectory) {
        this.policy = policy;
        this.client = client;
        this.tempDirectory = tempDirectory;
    }

    // 标识该驱动处理 OBS 类型策略。
    @Override
    public StoragePolicyType type() {
        return StoragePolicyType.OBS;
    }

    // 声明 OBS 支持多段、范围读取与跨域配置。
    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(
                StorageCapability.MULTIPART_UPLOAD,
                StorageCapability.RANGE_READ,
                StorageCapability.CORS_CONFIGURABLE);
    }

    // 检查覆盖冲突后暂存并上传整个对象。
    @Override
    public Mono<StorageObject> put(StoragePutRequest request) {
        return Mono.fromCallable(() -> client.doesObjectExist(bucket(), request.key()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(exists -> exists && !request.overwrite() ? Mono.error(StorageDriverErrors.conflict()) : Mono.empty())
                .then(StorageIo.spool(request.body(), tempDirectory))
                .flatMap(path -> Mono.fromCallable(() -> {
                            var size = Files.size(path);
                            var result = client.putObject(new PutObjectRequest(
                                    bucket(), request.key(), path.toFile()));
                            return new StorageObject(request.key(), size, Instant.now(), result.getEtag());
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> StorageIo.deleteQuietly(path))
                        .onErrorMap(ObsException.class, this::mapError));
    }

    // 按可选字节范围读取对象并返回元数据。
    @Override
    public Mono<StorageReadResult> get(String key, StorageReadOptions options) {
        var range = options == null ? null : options.range();
        return Mono.fromCallable(() -> {
                    var metadata = client.getObjectMetadata(bucket(), key);
                    var total = metadata.getContentLength();
                    var request = new GetObjectRequest(bucket(), key);
                    if (range != null) {
                        request.setRangeStart(range.start());
                        request.setRangeEnd(range.end());
                    }
                    var object = client.getObject(request);
                    var objectMetadata = object.getMetadata();
                    var length = objectMetadata.getContentLength();
                    var start = range == null ? 0 : range.start();
                    var end = range == null
                            ? Math.max(-1, total - 1)
                            : Math.min(range.end(), Math.max(0, total - 1));
                    var storageObject = new StorageObject(
                            key,
                            total,
                            objectMetadata.getLastModified() == null
                                    ? null
                                    : objectMetadata.getLastModified().toInstant(),
                            objectMetadata.getEtag());
                    var body = StorageIo.read(object.getObjectContent(), length);
                    return new StorageReadResult(storageObject, start, end, length, body);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(ObsException.class, this::mapError);
    }

    // 查询 OBS 对象元数据。
    @Override
    public Mono<StorageObject> stat(String key) {
        return Mono.fromCallable(() -> {
                    var metadata = client.getObjectMetadata(bucket(), key);
                    return new StorageObject(
                            key,
                            metadata.getContentLength(),
                            metadata.getLastModified() == null ? null : metadata.getLastModified().toInstant(),
                            metadata.getEtag());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(ObsException.class, this::mapError);
    }

    // 逐个删除对象并映射客户端异常。
    @Override
    public Mono<Void> delete(Collection<String> keys) {
        return Flux.fromIterable(keys)
                .concatMap(key -> Mono.fromRunnable(() -> client.deleteObject(bucket(), key))
                        .subscribeOn(Schedulers.boundedElastic()))
                .then()
                .onErrorMap(ObsException.class, this::mapError);
    }

    // 该驱动通过服务端代理下载，不返回直链。
    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        return Mono.just(new StorageSource(null, null, true));
    }

    // 通过存储桶是否存在判断连接状态。
    @Override
    public Mono<StorageTestResult> testConnection() {
        return Mono.fromCallable(() -> {
                    var exists = client.headBucket(bucket());
                    return new StorageTestResult(
                            exists,
                            exists ? "存储连接正常。" : "存储桶不存在或不可访问。",
                            Instant.now());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.just(new StorageTestResult(
                        false, "存储连接失败：" + safeMessage(error), Instant.now())));
    }

    // 为存储桶写入统一的跨域规则。
    @Override
    public Mono<Void> configureCors(List<String> origins) {
        return Mono.fromRunnable(() -> {
                    var rule = new BucketCorsRule();
                    rule.setAllowedOrigin(origins == null || origins.isEmpty() ? List.of("*") : origins);
                    rule.setAllowedMethod(List.of("GET", "HEAD", "PUT", "POST", "DELETE"));
                    rule.setAllowedHeader(List.of("*"));
                    rule.setExposeHeader(List.of("ETag"));
                    client.setBucketCors(new SetBucketCorsRequest(bucket(), new BucketCors(List.of(rule))));
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorMap(ObsException.class, this::mapError);
    }

    // 初始化 OBS 多段上传。
    @Override
    public Mono<NativeMultipartUpload> createMultipart(StorageMultipartRequest request) {
        return Mono.fromCallable(() -> {
                    var result = client.initiateMultipartUpload(
                            new InitiateMultipartUploadRequest(bucket(), request.key()));
                    return new NativeMultipartUpload(result.getUploadId(), request.key(), chunkSize());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(ObsException.class, this::mapError);
    }

    // 该驱动不支持预签名分片，直接返回错误。
    @Override
    public Mono<UploadPartUrl> presignPart(NativeMultipartUpload upload, int partNumber, Duration ttl) {
        return Mono.error(new ApiException(
                HttpStatus.BAD_REQUEST, "storage-presign-unsupported", "当前存储不支持预签名分片。"));
    }

    // 暂存分片后通过 OBS 客户端上传。
    @Override
    public Mono<UploadedPart> putPart(
            NativeMultipartUpload upload, int partNumber, Flux<org.springframework.core.io.buffer.DataBuffer> body, long size) {
        return StorageIo.spool(body, tempDirectory)
                .flatMap(path -> Mono.fromCallable(() -> {
                            var partSize = size >= 0 ? size : Files.size(path);
                            var request = new UploadPartRequest(bucket(), upload.key(), upload.uploadId());
                            request.setPartNumber(partNumber);
                            request.setPartSize(partSize);
                            request.setAutoClose(true);
                            request.setInput(Files.newInputStream(path));
                            var result = client.uploadPart(request);
                            return new UploadedPart(partNumber, result.getEtag(), partSize);
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> StorageIo.deleteQuietly(path))
                        .onErrorMap(ObsException.class, this::mapError));
    }

    // 提交分片 ETag 并返回完成后的对象信息。
    @Override
    public Mono<StorageObject> completeMultipart(NativeMultipartUpload upload, List<UploadedPart> parts) {
        return Mono.fromCallable(() -> {
                    var tags = parts.stream()
                            .map(part -> new PartEtag(part.etag(), part.partNumber()))
                            .toList();
                    client.completeMultipartUpload(new CompleteMultipartUploadRequest(
                            bucket(), upload.key(), upload.uploadId(), tags));
                    return statBlocking(upload.key());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(ObsException.class, this::mapError);
    }

    // 终止 OBS 多段上传。
    @Override
    public Mono<Void> abortMultipart(NativeMultipartUpload upload) {
        return Mono.fromRunnable(() -> client.abortMultipartUpload(new AbortMultipartUploadRequest(
                        bucket(), upload.key(), upload.uploadId())))
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorMap(ObsException.class, this::mapError);
    }

    // 分页列出 OBS 已接收的分片。
    @Override
    public Mono<List<UploadedPart>> listParts(NativeMultipartUpload upload) {
        return listPartsPage(upload, null);
    }

    // 递归拉取全部分片页并合并结果。
    private Mono<List<UploadedPart>> listPartsPage(NativeMultipartUpload upload, Integer marker) {
        return Mono.fromCallable(() -> {
                    var request = new ListPartsRequest(bucket(), upload.key(), upload.uploadId());
                    if (marker != null) {
                        request.setPartNumberMarker(marker);
                    }
                    return client.listParts(request);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(listing -> {
                    var page = listing.getMultipartList().stream()
                            .map(part -> new UploadedPart(
                                    part.getPartNumber(),
                                    part.getEtag(),
                                    part.getSize() == null ? 0 : part.getSize()))
                            .toList();
                    if (listing.isTruncated() && StringUtils.hasText(listing.getNextPartNumberMarker())) {
                        var nextMarker = Integer.parseInt(listing.getNextPartNumberMarker());
                        return listPartsPage(upload, nextMarker)
                                .map(next -> MultipartStorageDriver.mergeParts(page, next));
                    }
                    return Mono.just(page);
                })
                .onErrorMap(ObsException.class, this::mapError);
    }

    // 关闭 OBS 客户端并忽略关闭异常。
    @Override
    public void close() {
        try {
            client.close();
        } catch (IOException ignored) {
        }
    }

    // 返回策略分片大小，未配置时使用默认值。
    private long chunkSize() {
        var configured = policy.getSpec().getChunkSize();
        return configured > 0 ? configured : DEFAULT_CHUNK_SIZE;
    }

    // 同步查询元数据，供完成上传后使用。
    private StorageObject statBlocking(String key) {
        var metadata = client.getObjectMetadata(bucket(), key);
        return new StorageObject(
                key,
                metadata.getContentLength(),
                metadata.getLastModified() == null ? null : metadata.getLastModified().toInstant(),
                metadata.getEtag());
    }

    // 返回策略配置的存储桶名称。
    private String bucket() {
        return policy.getSpec().getBucketName();
    }

    // 将 OBS 错误码映射为统一的 API 错误。
    private ApiException mapError(ObsException error) {
        var code = error.getErrorCode();
        if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
            return StorageDriverErrors.notFound();
        }
        if ("AccessDenied".equals(code) || "InvalidAccessKeyId".equals(code)
                || "SignatureDoesNotMatch".equals(code)) {
            return new ApiException(HttpStatus.FORBIDDEN, "storage-access-denied", "存储访问被拒绝。");
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败。", error);
    }

    // 提取可安全展示的错误信息。
    private static String safeMessage(Throwable error) {
        return error instanceof ObsException obs && StringUtils.hasText(obs.getErrorMessage())
                ? obs.getErrorMessage()
                : "存储服务不可用。";
    }


}
