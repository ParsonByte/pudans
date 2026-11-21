package cn.tolim.pudans.core.storage.driver.qiniu;

import cn.tolim.pudans.core.storage.driver.StorageDriverErrors;

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
import cn.tolim.pudans.infra.exception.ApiException;
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

    // 初始化客户端，并为代理下载启用地址校验。
    public QiniuStorageDriver(StoragePolicy policy, Auth auth, Path tempDirectory) {
        this(
                policy,
                auth,
                new UploadManager(configuration()),
                new BucketManager(auth, configuration()),
                tempDirectory,
                // 代理下载在连接期重新校验解析地址，防止下载域名指向内网。
                WebClient.builder()
                        .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                                .resolver(new ValidatingAddressResolverGroup(new OutboundRequestValidator()))))
                        .build());
    }

    // 构造带出站地址校验的七牛配置
    private static Configuration configuration() {
        var configuration = new Configuration(Region.autoRegion());
        configuration.dns = new ValidatingQiniuDns();
        return configuration;
    }

    // 注入客户端依赖便于测试替换网络调用。
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

    // 标识该驱动处理七牛云类型策略。
    @Override
    public StoragePolicyType type() {
        return StoragePolicyType.QINIU;
    }

    // 声明七牛云支持直链、直传与范围读取。
    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(
                StorageCapability.PRESIGNED_SOURCE,
                StorageCapability.DIRECT_UPLOAD,
                StorageCapability.RANGE_READ);
    }

    // 生成带大小限制的七牛直传凭证。
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

    // 暂存文件后通过七牛上传管理器写入。
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

    // 通过下载地址读取内容并支持范围请求。
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
                .onStatus(status -> status.value() == 404 || status.value() == 612, response -> Mono.error(StorageDriverErrors.notFound()))
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

    // 查询七牛对象元数据。
    @Override
    public Mono<StorageObject> stat(String key) {
        return Mono.fromCallable(() -> statBlocking(key))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(QiniuException.class, this::mapError);
    }

    // 逐个删除对象并忽略缺失项。
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

    // 生成七牛下载直链。
    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        var ttl = options != null && options.ttl() != null ? options.ttl() : DEFAULT_SOURCE_TTL;
        return Mono.just(new StorageSource(
                URI.create(downloadUrl(key, ttl)), Instant.now().plus(ttl), false));
    }

    // 通过存储桶列表检测连接状态。
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

    // 无长期持有的外部连接需要关闭。
    @Override
    public void close() {}

    // 同步查询元数据并转换时间单位。
    private StorageObject statBlocking(String key) throws QiniuException {
        var info = bucketManager.stat(bucket(), key);
        return new StorageObject(
                key,
                info.fsize,
                Instant.ofEpochSecond(info.putTime / 10_000_000L),
                info.hash);
    }

    // 拼接下载域名，私有桶附加签名。
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

    // 返回策略配置的存储桶名称。
    private String bucket() {
        return policy.getSpec().getBucketName();
    }

    // 检查七牛响应，失败时抛出异常。
    private static void ensureOk(Response response) throws QiniuException {
        if (response != null && !response.isOK()) {
            throw new QiniuException(response);
        }
    }

    // 从 Content-Range 解析对象总大小。
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

    // 将七牛错误码映射为统一的 API 错误。
    private ApiException mapError(QiniuException error) {
        var code = error.code();
        if (code == 404 || code == 612) {
            return StorageDriverErrors.notFound();
        }
        if (code == 401 || code == 403) {
            return forbidden();
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败（" + code + "）。", error);
    }

    // 提取可安全展示的错误信息。
    private static String safeMessage(Throwable error) {
        if (error instanceof QiniuException qiniu && StringUtils.hasText(qiniu.error())) {
            return qiniu.error();
        }
        return "存储服务不可用。";
    }


    // 构造存储访问被拒绝错误。
    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "storage-access-denied", "存储访问被拒绝。");
    }

    // 构造存储服务请求失败错误。
    private static ApiException driverError() {
        return new ApiException(HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败。");
    }
}
