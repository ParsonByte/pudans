package cn.tolim.pudans.core.storage.driver.qiniu;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver.DirectUploadCredential;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver.StorageDirectUploadRequest;
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
import cn.tolim.pudans.core.webhook.OutboundRequestValidator;
import cn.tolim.pudans.core.webhook.ValidatingAddressResolverGroup;
import cn.tolim.pudans.core.infra.exception.ApiException;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.http.client.HttpClient;

public class QiniuStorageDriver implements StorageDriver, DirectUploadDriver {

    private static final Duration DEFAULT_SOURCE_TTL = Duration.ofMinutes(15);

    private final StoragePolicy policy;

    private final Auth auth;

    private final UploadManager uploadManager;

    private final BucketManager bucketManager;

    private final Path tempDirectory;

    private final WebClient webClient;

    public QiniuStorageDriver(StoragePolicy policy, Auth auth, Path tempDirectory) {
        this(
                policy,
                auth,
                new UploadManager(new Configuration(Region.autoRegion())),
                new BucketManager(auth, new Configuration(Region.autoRegion())),
                tempDirectory,
                // 代理下载在连接期重新校验解析地址，防止下载域名指向内网。
                WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                                .resolver(new ValidatingAddressResolverGroup(new OutboundRequestValidator()))))
                        .build());
    }

    QiniuStorageDriver(
            StoragePolicy policy,
            Auth auth,
            UploadManager uploadManager,
            BucketManager bucketManager,
            Path tempDirectory,
            WebClient webClient) {
        this.policy = policy;
        this.auth = auth;
        this.uploadManager = uploadManager;
        this.bucketManager = bucketManager;
        this.tempDirectory = tempDirectory;
        this.webClient = webClient;
    }

    @Override
    public StoragePolicyType type() {
        return StoragePolicyType.QINIU;
    }

    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(
                StorageCapability.PRESIGNED_SOURCE,
                StorageCapability.DIRECT_UPLOAD,
                StorageCapability.RANGE_READ);
    }

    @Override
    public Mono<DirectUploadCredential> createDirectUpload(StorageDirectUploadRequest request) {
        return Mono.fromCallable(() -> {
                    var expiresAt = request.expiresAt() == null
                            ? Instant.now().plusSeconds(3600)
                            : request.expiresAt();
                    var seconds = Math.max(3600, Duration.between(Instant.now(), expiresAt).getSeconds());
                    var maxSize = request.size() > 0 ? request.size() : policy.getSpec().getMaxSize();
                    var policyMap = new StringMap();
                    if (maxSize > 0) {
                        policyMap.put("fsizeLimit", maxSize);
                    }
                    var token = auth.uploadToken(bucket(), request.key(), seconds, policyMap);
                    return new DirectUploadCredential(
                            "qiniu",
                            "https://upload.qiniup.com",
                            token,
                            null,
                            null,
                            request.key(),
                            request.contentType(),
                            expiresAt);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<StorageObject> put(StoragePutRequest request) {
        return StorageIo.spool(request.body(), tempDirectory)
                .flatMap(path -> Mono.fromCallable(() -> {
                            var response = uploadManager.put(
                                    path.toFile(), request.key(), auth.uploadToken(bucket()));
                            ensureOk(response);
                            return statBlocking(request.key());
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> StorageIo.deleteQuietly(path))
                        .onErrorMap(QiniuException.class, this::mapError));
    }

    @Override
    public Mono<StorageReadResult> get(String key, StorageReadOptions options) {
        var range = options == null ? null : options.range();
        var url = downloadUrl(key, DEFAULT_SOURCE_TTL);
        return webClient
                .get()
                .uri(URI.create(url))
                .headers(headers -> {
                    if (range != null) {
                        headers.set(HttpHeaders.RANGE, "bytes=" + range.start() + "-" + range.end());
                    }
                })
                .retrieve()
                .onStatus(status -> status.value() == 404 || status.value() == 612, response -> Mono.error(notFound()))
                .onStatus(status -> status.value() == 401 || status.value() == 403, response -> Mono.error(forbidden()))
                .onStatus(HttpStatusCode::isError, response -> Mono.error(driverError()))
                .toEntityFlux(DataBuffer.class)
                .map(entity -> {
                    var headers = entity.getHeaders();
                    var length = headers.getContentLength();
                    var total = totalSize(headers.getFirst(HttpHeaders.CONTENT_RANGE), length);
                    var start = range == null ? 0 : range.start();
                    var end = range == null
                            ? Math.max(-1, total - 1)
                            : Math.min(range.end(), Math.max(0, total - 1));
                    var object = new StorageObject(
                            key,
                            total,
                            headers.getLastModified() > 0
                                    ? Instant.ofEpochMilli(headers.getLastModified())
                                    : null,
                            headers.getETag());
                    return new StorageReadResult(object, start, end, length, entity.getBody());
                });
    }

    @Override
    public Mono<StorageObject> stat(String key) {
        return Mono.fromCallable(() -> statBlocking(key))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(QiniuException.class, this::mapError);
    }

    @Override
    public Mono<Void> delete(Collection<String> keys) {
        return Flux.fromIterable(keys)
                .concatMap(key -> Mono.fromCallable(() -> {
                            ensureOk(bucketManager.delete(bucket(), key));
                            return true;
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorResume(QiniuException.class, error -> {
                            var code = error.code();
                            return code == 404 || code == 612 ? Mono.empty() : Mono.error(mapError(error));
                        }))
                .then()
                .onErrorMap(QiniuException.class, this::mapError);
    }

    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        var ttl = options != null && options.ttl() != null ? options.ttl() : DEFAULT_SOURCE_TTL;
        return Mono.just(new StorageSource(
                URI.create(downloadUrl(key, ttl)), Instant.now().plus(ttl), false));
    }

    @Override
    public Mono<StorageTestResult> testConnection() {
        return Mono.fromCallable(() -> {
                    var buckets = bucketManager.buckets();
                    var exists = java.util.Arrays.asList(buckets).contains(bucket());
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
    public void close() {}

    private StorageObject statBlocking(String key) throws QiniuException {
        var info = bucketManager.stat(bucket(), key);
        return new StorageObject(
                key,
                info.fsize,
                Instant.ofEpochSecond(info.putTime / 10_000_000L),
                info.hash);
    }

    private String downloadUrl(String key, Duration ttl) {
        var base = policy.getSpec().getServer();
        if (!StringUtils.hasText(base)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-endpoint-missing", "七牛存储未配置下载域名。");
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "https://" + base;
        }
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        var url = base + "/" + StorageIo.encodePath(key);
        if (policy.getSpec().isPrivateBucket()) {
            return auth.privateDownloadUrl(url, Math.max(1, ttl.getSeconds()));
        }
        return url;
    }

    private String bucket() {
        return policy.getSpec().getBucketName();
    }

    private static void ensureOk(Response response) throws QiniuException {
        if (response != null && !response.isOK()) {
            throw new QiniuException(response);
        }
    }

    private static long totalSize(String contentRange, long contentLength) {
        if (StringUtils.hasText(contentRange)) {
            var slash = contentRange.lastIndexOf('/');
            if (slash >= 0 && slash < contentRange.length() - 1) {
                try {
                    return Long.parseLong(contentRange.substring(slash + 1).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return Math.max(0, contentLength);
    }

    private ApiException mapError(QiniuException error) {
        var code = error.code();
        if (code == 404 || code == 612) {
            return notFound();
        }
        if (code == 401 || code == 403) {
            return forbidden();
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败（" + code + "）。", error);
    }

    private static String safeMessage(Throwable error) {
        if (error instanceof QiniuException qiniu && StringUtils.hasText(qiniu.error())) {
            return qiniu.error();
        }
        return "存储服务不可用。";
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "storage-object-not-found", "文件不存在。");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "storage-access-denied", "存储访问被拒绝。");
    }

    private static ApiException driverError() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败。");
    }
}
