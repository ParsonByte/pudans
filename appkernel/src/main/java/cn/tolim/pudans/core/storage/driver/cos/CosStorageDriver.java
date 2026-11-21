package cn.tolim.pudans.core.storage.driver.cos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.resource.StoragePolicy;
import cn.tolim.pudans.contract.storage.resource.StoragePolicyType;
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
import com.qcloud.cos.COSClient;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.AbortMultipartUploadRequest;
import com.qcloud.cos.model.BucketCrossOriginConfiguration;
import com.qcloud.cos.model.CORSRule;
import com.qcloud.cos.model.CompleteMultipartUploadRequest;
import com.qcloud.cos.model.GetObjectRequest;
import com.qcloud.cos.model.InitiateMultipartUploadRequest;
import com.qcloud.cos.model.ListPartsRequest;
import com.qcloud.cos.model.PartETag;
import com.qcloud.cos.model.UploadPartRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public class CosStorageDriver implements StorageDriver, MultipartStorageDriver, cn.tolim.pudans.core.storage.driver.CorsConfigurable {

    private static final Duration DEFAULT_SOURCE_TTL = Duration.ofMinutes(15);

    private static final long DEFAULT_CHUNK_SIZE = 8L * 1024L * 1024L;

    private final StoragePolicy policy;

    private final COSClient client;

    private final Path tempDirectory;

    public CosStorageDriver(StoragePolicy policy, COSClient client, Path tempDirectory) {
        this.policy = policy;
        this.client = client;
        this.tempDirectory = tempDirectory;
    }

    @Override
    public StoragePolicyType type() {
        return StoragePolicyType.COS;
    }

    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(
                StorageCapability.PRESIGNED_SOURCE,
                StorageCapability.MULTIPART_UPLOAD,
                StorageCapability.RANGE_READ,
                StorageCapability.CORS_CONFIGURABLE);
    }

    @Override
    public Mono<StorageObject> put(StoragePutRequest request) {
        return Mono.fromCallable(() -> client.doesObjectExist(bucket(), request.key()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(exists -> exists && !request.overwrite() ? Mono.error(conflict()) : Mono.empty())
                .then(StorageIo.spool(request.body(), tempDirectory))
                .flatMap(path -> Mono.fromCallable(() -> {
                            var result = client.putObject(bucket(), request.key(), path.toFile());
                            return new StorageObject(request.key(), request.size(), Instant.now(), result.getETag());
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> StorageIo.deleteQuietly(path))
                        .onErrorMap(CosClientException.class, this::mapError));
    }

    @Override
    public Mono<StorageReadResult> get(String key, StorageReadOptions options) {
        var range = options == null ? null : options.range();
        return Mono.fromCallable(() -> {
                    var metadata = client.getObjectMetadata(bucket(), key);
                    var total = metadata.getContentLength();
                    var request = new GetObjectRequest(bucket(), key);
                    if (range != null) {
                        request.setRange(range.start(), range.end());
                    }
                    var object = client.getObject(request);
                    var objectMetadata = object.getObjectMetadata();
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
                            objectMetadata.getETag());
                    var body = StorageIo.read(object.getObjectContent(), length);
                    return new StorageReadResult(storageObject, start, end, length, body);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(CosClientException.class, this::mapError);
    }

    @Override
    public Mono<StorageObject> stat(String key) {
        return Mono.fromCallable(() -> {
                    var metadata = client.getObjectMetadata(bucket(), key);
                    return new StorageObject(
                            key,
                            metadata.getContentLength(),
                            metadata.getLastModified() == null ? null : metadata.getLastModified().toInstant(),
                            metadata.getETag());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(CosClientException.class, this::mapError);
    }

    @Override
    public Mono<Void> delete(Collection<String> keys) {
        return Flux.fromIterable(keys)
                .concatMap(key -> Mono.fromRunnable(() -> client.deleteObject(bucket(), key))
                        .subscribeOn(Schedulers.boundedElastic()))
                .then()
                .onErrorMap(CosClientException.class, this::mapError);
    }

    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        var ttl = options != null && options.ttl() != null ? options.ttl() : DEFAULT_SOURCE_TTL;
        return Mono.fromCallable(() -> {
                    var url = client.generatePresignedUrl(
                            bucket(), key, Date.from(Instant.now().plus(ttl)), HttpMethodName.GET);
                    return new StorageSource(url.toURI(), Instant.now().plus(ttl), false);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<StorageTestResult> testConnection() {
        return Mono.fromCallable(() -> {
                    var exists = client.doesBucketExist(bucket());
                    return new StorageTestResult(
                            exists,
                            exists ? "存储连接正常。" : "存储桶不存在或不可访问。",
                            Instant.now());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.just(new StorageTestResult(
                        false, "存储连接失败：" + safeMessage(error), Instant.now())));
    }

    @Override
    public Mono<Void> configureCors(List<String> origins) {
        return Mono.fromRunnable(() -> {
                    var rule = new CORSRule();
                    rule.setId("pudans");
                    rule.setAllowedOrigins(origins == null || origins.isEmpty() ? List.of("*") : origins);
                    rule.setAllowedMethods(
                            CORSRule.AllowedMethods.GET,
                            CORSRule.AllowedMethods.HEAD,
                            CORSRule.AllowedMethods.PUT,
                            CORSRule.AllowedMethods.POST,
                            CORSRule.AllowedMethods.DELETE);
                    rule.setAllowedHeaders(List.of("*"));
                    rule.setExposedHeaders(List.of("ETag"));
                    rule.setMaxAgeSeconds(3600);
                    var configuration = new BucketCrossOriginConfiguration();
                    configuration.setRules(List.of(rule));
                    client.setBucketCrossOriginConfiguration(bucket(), configuration);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorMap(CosClientException.class, this::mapError);
    }

    @Override
    public Mono<NativeMultipartUpload> createMultipart(StorageMultipartRequest request) {
        return Mono.fromCallable(() -> {
                    var result = client.initiateMultipartUpload(
                            new InitiateMultipartUploadRequest(bucket(), request.key()));
                    return new NativeMultipartUpload(result.getUploadId(), request.key(), chunkSize());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(CosClientException.class, this::mapError);
    }

    @Override
    public Mono<UploadPartUrl> presignPart(NativeMultipartUpload upload, int partNumber, Duration ttl) {
        return Mono.error(new ApiException(
                HttpStatus.BAD_REQUEST, "storage-presign-unsupported", "当前存储不支持预签名分片。"));
    }

    @Override
    public Mono<UploadedPart> putPart(
            NativeMultipartUpload upload, int partNumber, Flux<org.springframework.core.io.buffer.DataBuffer> body, long size) {
        return StorageIo.spool(body, tempDirectory)
                .flatMap(path -> Mono.fromCallable(() -> {
                            var partSize = size >= 0 ? size : Files.size(path);
                            try (var input = Files.newInputStream(path)) {
                                var request = new UploadPartRequest();
                                request.setBucketName(bucket());
                                request.setKey(upload.key());
                                request.setUploadId(upload.uploadId());
                                request.setPartNumber(partNumber);
                                request.setPartSize(partSize);
                                request.setInputStream(input);
                                var result = client.uploadPart(request);
                                return new UploadedPart(partNumber, result.getETag(), partSize);
                            }
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> StorageIo.deleteQuietly(path))
                        .onErrorMap(CosClientException.class, this::mapError));
    }

    @Override
    public Mono<StorageObject> completeMultipart(NativeMultipartUpload upload, List<UploadedPart> parts) {
        return Mono.fromCallable(() -> {
                    var tags = parts.stream()
                            .map(part -> new PartETag(part.partNumber(), part.etag()))
                            .toList();
                    client.completeMultipartUpload(new CompleteMultipartUploadRequest(
                            bucket(), upload.key(), upload.uploadId(), tags));
                    return statBlocking(upload.key());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(CosClientException.class, this::mapError);
    }

    @Override
    public Mono<Void> abortMultipart(NativeMultipartUpload upload) {
        return Mono.fromRunnable(() -> client.abortMultipartUpload(new AbortMultipartUploadRequest(
                        bucket(), upload.key(), upload.uploadId())))
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorMap(CosClientException.class, this::mapError);
    }

    @Override
    public Mono<List<UploadedPart>> listParts(NativeMultipartUpload upload) {
        return listPartsPage(upload, null);
    }

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
                    var page = listing.getParts().stream()
                            .map(part -> new UploadedPart(part.getPartNumber(), part.getETag(), part.getSize()))
                            .toList();
                    if (listing.isTruncated() && listing.getNextPartNumberMarker() != null) {
                        return listPartsPage(upload, listing.getNextPartNumberMarker())
                                .map(next -> MultipartStorageDriver.mergeParts(page, next));
                    }
                    return Mono.just(page);
                })
                .onErrorMap(CosClientException.class, this::mapError);
    }

    @Override
    public void close() {
        client.shutdown();
    }

    private long chunkSize() {
        var configured = policy.getSpec().getChunkSize();
        return configured > 0 ? configured : DEFAULT_CHUNK_SIZE;
    }

    private StorageObject statBlocking(String key) {
        var metadata = client.getObjectMetadata(bucket(), key);
        return new StorageObject(
                key,
                metadata.getContentLength(),
                metadata.getLastModified() == null ? null : metadata.getLastModified().toInstant(),
                metadata.getETag());
    }

    private String bucket() {
        return policy.getSpec().getBucketName();
    }

    private ApiException mapError(CosClientException error) {
        if (error instanceof CosServiceException service) {
            if (service.getStatusCode() == 404 || "NoSuchKey".equals(service.getErrorCode())) {
                return notFound();
            }
            if (service.getStatusCode() == 403) {
                return new ApiException(HttpStatus.FORBIDDEN, "storage-access-denied", "存储访问被拒绝。");
            }
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败。", error);
    }

    private static String safeMessage(Throwable error) {
        return error instanceof CosServiceException service && StringUtils.hasText(service.getErrorMessage())
                ? service.getErrorMessage()
                : "存储服务不可用。";
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "storage-object-not-found", "文件不存在。");
    }

    private static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "storage-object-exists", "文件已存在。");
    }
}
