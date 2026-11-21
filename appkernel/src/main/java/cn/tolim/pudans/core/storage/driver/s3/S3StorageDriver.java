package cn.tolim.pudans.core.storage.driver.s3;

import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
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
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StoragePutRequest;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.core.storage.driver.StorageSource;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.core.infra.exception.ApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.async.AsyncResponseTransformer;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.AbortMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CORSConfiguration;
import software.amazon.awssdk.services.s3.model.CORSRule;
import software.amazon.awssdk.services.s3.model.CompleteMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.CompletedMultipartUpload;
import software.amazon.awssdk.services.s3.model.CompletedPart;
import software.amazon.awssdk.services.s3.model.CreateMultipartUploadRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListPartsRequest;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutBucketCorsRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.UploadPartRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.UploadPartPresignRequest;

public class S3StorageDriver implements StorageDriver, MultipartStorageDriver, CorsConfigurable {

    private static final Duration DEFAULT_SOURCE_TTL = Duration.ofMinutes(15);

    private static final long DEFAULT_CHUNK_SIZE = 25L * 1024L * 1024L;

    private static final int MAX_DELETE_BATCH_SIZE = 1000;

    private final StoragePolicy policy;

    private final String bucket;

    private final S3AsyncClient client;

    private final S3Presigner presigner;

    public S3StorageDriver(StoragePolicy policy, S3AsyncClient client, S3Presigner presigner) {
        this.policy = policy;
        this.bucket = policy.getSpec().getBucketName();
        this.client = client;
        this.presigner = presigner;
    }

    @Override
    public StoragePolicyType type() {
        return policy.getSpec().getType();
    }

    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(
                StorageCapability.PRESIGNED_SOURCE,
                StorageCapability.PRESIGNED_PARTS,
                StorageCapability.MULTIPART_UPLOAD,
                StorageCapability.RANGE_READ,
                StorageCapability.CORS_CONFIGURABLE);
    }

    @Override
    public Mono<StorageObject> put(StoragePutRequest request) {
        return headObject(request.key())
                .onErrorResume(error -> isNotFound(error) ? Mono.empty() : Mono.error(error))
                .flatMap(existing -> request.overwrite() ? Mono.empty() : Mono.error(conflict()))
                .then(Mono.defer(() -> {
                    var builder = PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(request.key())
                            .contentType(contentType(request.contentType()));
                    if (request.size() >= 0) {
                        builder.contentLength(request.size());
                    }
                    return Mono.fromFuture(client.putObject(builder.build(), AsyncRequestBody.fromPublisher(copyBody(request.body()))))
                            .map(response -> new StorageObject(
                                    request.key(), request.size(), Instant.now(), etag(response.eTag())))
                            .onErrorMap(S3Exception.class, this::mapError);
                }));
    }

    @Override
    public Mono<StorageReadResult> get(String key, StorageReadOptions options) {
        var range = options == null ? null : options.range();
        var builder = GetObjectRequest.builder().bucket(bucket).key(key);
        if (range != null) {
            builder.range("bytes=" + range.start() + "-" + range.end());
        }
        return Mono.fromFuture(client.getObject(builder.build(), AsyncResponseTransformer.toPublisher()))
                .map(publisher -> {
                    var response = publisher.response();
                    var total = totalSize(response.contentRange(), response.contentLength());
                    var start = range == null ? 0 : range.start();
                    var end = range == null
                            ? Math.max(-1, total - 1)
                            : Math.min(range.end(), Math.max(0, total - 1));
                    var length = response.contentLength() != null
                            ? response.contentLength()
                            : Math.max(0, end - start + 1);
                    var object = new StorageObject(key, total, response.lastModified(), etag(response.eTag()));
                    var body = Flux.from(publisher)
                            .<DataBuffer>map(buffer -> DefaultDataBufferFactory.sharedInstance.wrap(buffer));
                    return new StorageReadResult(object, start, end, length, body);
                })
                .onErrorMap(S3Exception.class, this::mapError);
    }

    @Override
    public Mono<StorageObject> stat(String key) {
        return headObject(key);
    }

    @Override
    public Mono<Void> delete(Collection<String> keys) {
        return Flux.fromIterable(keys)
                .buffer(deleteBatchSize())
                .concatMap(batch -> {
                    var objects = batch.stream()
                            .map(key -> ObjectIdentifier.builder().key(key).build())
                            .toList();
                    var request = DeleteObjectsRequest.builder()
                            .bucket(bucket)
                            .delete(Delete.builder().objects(objects).build())
                            .build();
                    return Mono.fromFuture(client.deleteObjects(request));
                })
                .then()
                .onErrorMap(S3Exception.class, this::mapError);
    }

    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        var ttl = options != null && options.ttl() != null ? options.ttl() : DEFAULT_SOURCE_TTL;
        var request = GetObjectRequest.builder().bucket(bucket).key(key).build();
        var presigned = GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(request)
                .build();
        var url = presigner.presignGetObject(presigned).url();
        return Mono.just(new StorageSource(toUri(url), Instant.now().plus(ttl), false));
    }

    @Override
    public Mono<StorageTestResult> testConnection() {
        return headBucket()
                .thenReturn(new StorageTestResult(true, "存储连接正常。", Instant.now()))
                .onErrorResume(error -> listBucket()
                        .thenReturn(new StorageTestResult(true, "存储连接正常。", Instant.now()))
                        .onErrorResume(listError -> Mono.just(new StorageTestResult(
                                false, "存储连接失败：" + safeMessage(listError), Instant.now()))));
    }

    @Override
    public Mono<Void> configureCors(List<String> origins) {
        var allowed = origins == null || origins.isEmpty() ? List.of("*") : origins;
        var rule = CORSRule.builder()
                .allowedOrigins(allowed)
                .allowedMethods(List.of("GET", "HEAD", "PUT", "POST", "DELETE"))
                .allowedHeaders(List.of("*"))
                .exposeHeaders(List.of("ETag"))
                .build();
        var configuration = CORSConfiguration.builder().corsRules(List.of(rule)).build();
        var request = PutBucketCorsRequest.builder()
                .bucket(bucket)
                .corsConfiguration(configuration)
                .build();
        return Mono.fromFuture(client.putBucketCors(request))
                .then()
                .onErrorMap(S3Exception.class, this::mapError);
    }

    @Override
    public Mono<NativeMultipartUpload> createMultipart(StorageMultipartRequest request) {
        var builder = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(request.key())
                .contentType(contentType(request.contentType()));
        return Mono.fromFuture(client.createMultipartUpload(builder.build()))
                .map(response -> new NativeMultipartUpload(response.uploadId(), request.key(), chunkSize()))
                .onErrorMap(S3Exception.class, this::mapError);
    }

    @Override
    public Mono<UploadPartUrl> presignPart(NativeMultipartUpload upload, int partNumber, Duration ttl) {
        var request = UploadPartRequest.builder()
                .bucket(bucket)
                .key(upload.key())
                .uploadId(upload.uploadId())
                .partNumber(partNumber)
                .build();
        var presigned = UploadPartPresignRequest.builder()
                .signatureDuration(ttl)
                .uploadPartRequest(request)
                .build();
        var url = presigner.presignUploadPart(presigned).url();
        return Mono.just(new UploadPartUrl(partNumber, toUri(url), Instant.now().plus(ttl)));
    }

    @Override
    public Mono<UploadedPart> putPart(
            NativeMultipartUpload upload, int partNumber, Flux<DataBuffer> body, long size) {
        var builder = UploadPartRequest.builder()
                .bucket(bucket)
                .key(upload.key())
                .uploadId(upload.uploadId())
                .partNumber(partNumber);
        if (size >= 0) {
            builder.contentLength(size);
        }
        return Mono.fromFuture(client.uploadPart(builder.build(), AsyncRequestBody.fromPublisher(copyBody(body))))
                .map(response -> new UploadedPart(partNumber, response.eTag(), size))
                .onErrorMap(S3Exception.class, this::mapError);
    }

    @Override
    public Mono<StorageObject> completeMultipart(NativeMultipartUpload upload, List<UploadedPart> parts) {
        var completed = parts.stream()
                .map(part -> CompletedPart.builder()
                        .partNumber(part.partNumber())
                        .eTag(part.etag())
                        .build())
                .toList();
        var request = CompleteMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(upload.key())
                .uploadId(upload.uploadId())
                .multipartUpload(CompletedMultipartUpload.builder().parts(completed).build())
                .build();
        return Mono.fromFuture(client.completeMultipartUpload(request))
                .flatMap(response -> stat(upload.key()))
                .onErrorMap(S3Exception.class, this::mapError);
    }

    @Override
    public Mono<Void> abortMultipart(NativeMultipartUpload upload) {
        var request = AbortMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(upload.key())
                .uploadId(upload.uploadId())
                .build();
        return Mono.fromFuture(client.abortMultipartUpload(request))
                .then()
                .onErrorMap(S3Exception.class, this::mapError);
    }

    @Override
    public Mono<List<UploadedPart>> listParts(NativeMultipartUpload upload) {
        return listPartsPage(upload, null);
    }

    private Mono<List<UploadedPart>> listPartsPage(NativeMultipartUpload upload, Integer marker) {
        var builder = ListPartsRequest.builder()
                .bucket(bucket)
                .key(upload.key())
                .uploadId(upload.uploadId());
        if (marker != null) {
            builder.partNumberMarker(marker);
        }
        return Mono.fromFuture(client.listParts(builder.build()))
                .flatMap(response -> {
                    var page = response.parts().stream()
                            .map(part -> new UploadedPart(
                                    part.partNumber(), part.eTag(), part.size() == null ? 0 : part.size()))
                            .toList();
                    if (Boolean.TRUE.equals(response.isTruncated())
                            && response.nextPartNumberMarker() != null) {
                        return listPartsPage(upload, response.nextPartNumberMarker())
                                .map(next -> MultipartStorageDriver.mergeParts(page, next));
                    }
                    return Mono.just(page);
                })
                .onErrorMap(S3Exception.class, this::mapError);
    }

    @Override
    public void close() {
        presigner.close();
        client.close();
    }

    private Mono<StorageObject> headObject(String key) {
        var request = HeadObjectRequest.builder().bucket(bucket).key(key).build();
        return Mono.fromFuture(client.headObject(request))
                .map(response -> new StorageObject(
                        key,
                        response.contentLength() == null ? 0 : response.contentLength(),
                        response.lastModified(),
                        etag(response.eTag())))
                .onErrorMap(S3Exception.class, error -> isNotFoundStatus(error) ? notFound() : mapError(error));
    }

    private Mono<Void> headBucket() {
        return Mono.fromFuture(client.headBucket(HeadBucketRequest.builder().bucket(bucket).build()))
                .then()
                .onErrorMap(S3Exception.class, this::mapError);
    }

    private Mono<Void> listBucket() {
        var request = ListObjectsV2Request.builder().bucket(bucket).maxKeys(1).build();
        return Mono.fromFuture(client.listObjectsV2(request))
                .then()
                .onErrorMap(S3Exception.class, this::mapError);
    }

    private long chunkSize() {
        var configured = policy.getSpec().getChunkSize();
        return configured > 0 ? configured : DEFAULT_CHUNK_SIZE;
    }

    private int deleteBatchSize() {
        var settings = policy.getSpec().getSettings();
        var configured = settings == null ? MAX_DELETE_BATCH_SIZE : settings.getDeleteBatchSize();
        return Math.max(1, Math.min(MAX_DELETE_BATCH_SIZE, configured));
    }

    private static Flux<ByteBuffer> copyBody(Flux<DataBuffer> body) {
        return body.map(buffer -> {
            try {
                var bytes = new byte[buffer.readableByteCount()];
                buffer.read(bytes);
                return ByteBuffer.wrap(bytes);
            } finally {
                DataBufferUtils.release(buffer);
            }
        });
    }

    private static long totalSize(String contentRange, Long contentLength) {
        if (StringUtils.hasText(contentRange)) {
            var slash = contentRange.lastIndexOf('/');
            if (slash >= 0 && slash < contentRange.length() - 1) {
                try {
                    return Long.parseLong(contentRange.substring(slash + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return contentLength == null ? 0 : contentLength;
    }

    private static String contentType(String value) {
        return StringUtils.hasText(value) ? value : "application/octet-stream";
    }

    private static String etag(String value) {
        return value == null ? null : value.replace("\"", "");
    }

    private static URI toUri(java.net.URL url) {
        try {
            return url.toURI();
        } catch (java.net.URISyntaxException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储直链无效。", e);
        }
    }

    private ApiException mapError(S3Exception error) {
        var status = error.statusCode();
        if (status == 404) {
            return notFound();
        }
        if (status == 403) {
            return new ApiException(HttpStatus.FORBIDDEN, "storage-access-denied", "存储访问被拒绝。");
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败（" + status + "）。", error);
    }

    private static boolean isNotFoundStatus(S3Exception error) {
        return error.statusCode() == 404;
    }

    private static boolean isNotFound(Throwable error) {
        return error instanceof ApiException api && "storage-object-not-found".equals(api.getCode());
    }

    private static String safeMessage(Throwable error) {
        return error instanceof ApiException api && api.getReason() != null
                ? api.getReason()
                : "存储服务不可用。";
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "storage-object-not-found", "文件不存在。");
    }

    private static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "storage-object-exists", "文件已存在。");
    }
}
