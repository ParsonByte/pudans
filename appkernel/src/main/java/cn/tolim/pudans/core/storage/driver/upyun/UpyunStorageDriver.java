package cn.tolim.pudans.core.storage.driver.upyun;

import cn.tolim.pudans.core.storage.driver.StorageDriverErrors;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.DirectUploadDriver;
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
import com.UpYun;
import com.upyun.UpException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.json.JsonMapper;

public class UpyunStorageDriver implements StorageDriver, DirectUploadDriver {

    private final StoragePolicy policy;

    private final UpYun client;

    private final String operator;

    private final String password;

    private final Path tempDirectory;

    // 保存策略、又拍云客户端与操作员凭据。
    public UpyunStorageDriver(
            StoragePolicy policy, UpYun client, String operator, String password, Path tempDirectory) {
        this.policy = policy;
        this.client = client;
        this.operator = operator;
        this.password = password;
        this.tempDirectory = tempDirectory;
    }

    // 标识该驱动处理又拍云类型策略。
    @Override
    public StoragePolicyType type() {
        return StoragePolicyType.UPYUN;
    }

    // 又拍云支持客户端直传与范围读取。
    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(StorageCapability.DIRECT_UPLOAD, StorageCapability.RANGE_READ);
    }

    // 生成表单直传所需的策略与签名。
    @Override
    public Mono<DirectUploadCredential> createDirectUpload(StorageDirectUploadRequest request) {
        return Mono.fromCallable(() -> {
                    var expiresAt = request.expiresAt() == null
                            ? Instant.now().plusSeconds(3600)
                            : request.expiresAt();
                    var policyJson = JsonMapper.shared().writeValueAsString(Map.of(
                            "bucket", bucket(),
                            "save-key", request.key(),
                            "expiration", expiresAt.getEpochSecond(),
                            "content-length", request.size(),
                            "content-length-range", "0," + request.size()));
                    var policy = Base64.getEncoder()
                            .encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
                    var signature = sign(operator, password, List.of("POST", "/" + bucket(), policy));
                    return new DirectUploadCredential(
                            "upyun",
                            "https://v0.api.upyun.com/" + bucket(),
                            null,
                            policy,
                            signature,
                            request.key(),
                            request.contentType(),
                            expiresAt);
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    // 检查覆盖冲突后暂存并写入文件。
    @Override
    public Mono<StorageObject> put(StoragePutRequest request) {
        return Mono.fromCallable(() -> fileInfo(request.key()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.empty())
                .flatMap(info -> request.overwrite() ? Mono.empty() : Mono.error(StorageDriverErrors.conflict()))
                .then(StorageIo.spool(request.body(), tempDirectory))
                .flatMap(path -> Mono.fromCallable(() -> {
                            var success = client.writeFile(request.key(), path.toFile(), true, Map.of());
                            if (!success) {
                                throw new ApiException(
                                        HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储写入失败。");
                            }
                            return statBlocking(request.key());
                        })
                        .subscribeOn(Schedulers.boundedElastic())
                        .doFinally(signal -> StorageIo.deleteQuietly(path))
                        .onErrorMap(UpException.class, this::mapError));
    }

    // 下载到临时文件后按范围读取并清理。
    @Override
    public Mono<StorageReadResult> get(String key, StorageReadOptions options) {
        var range = options == null ? null : options.range();
        return Mono.fromCallable(() -> {
                    var object = statBlocking(key);
                    var total = object.size();
                    ensureTempSpace(total);
                    var temp = Files.createTempFile(tempDirectory, ".download-", ".tmp");
                    try {
                        var success = client.readFile(key, temp.toFile());
                        if (!success) {
                            throw new ApiException(
                                    HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储读取失败。");
                        }
                        var start = range == null ? 0 : range.start();
                        var end = range == null
                                ? Math.max(-1, total - 1)
                                : Math.min(range.end(), Math.max(0, total - 1));
                        var length = total == 0 ? 0 : end - start + 1;
                        var input = Files.newInputStream(temp);
                        if (start > 0) {
                            input.skipNBytes(start);
                        }
                        var body = StorageIo.read(input, length)
                                .doFinally(signal -> StorageIo.deleteQuietly(temp));
                        return new StorageReadResult(object, start, end, length, body);
                    } catch (IOException | UpException | RuntimeException e) {
                        StorageIo.deleteQuietly(temp);
                        throw e;
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(UpException.class, this::mapError)
                .onErrorMap(IOException.class, this::ioError);
    }

    // 临时盘容不下整份对象时直接拒绝，避免 Range 下载写满磁盘。
    private void ensureTempSpace(long total) {
        StorageIo.ensureUsableSpace(tempDirectory, total);
    }

    // 查询又拍云对象元数据。
    @Override
    public Mono<StorageObject> stat(String key) {
        return Mono.fromCallable(() -> statBlocking(key))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(UpException.class, this::mapError);
    }

    // 逐个删除对象并映射客户端异常。
    @Override
    public Mono<Void> delete(Collection<String> keys) {
        return Flux.fromIterable(keys)
                .concatMap(key -> Mono.fromCallable(() -> {
                            client.deleteFile(key, Map.of());
                            return true;
                        })
                        .subscribeOn(Schedulers.boundedElastic()))
                .then()
                .onErrorMap(UpException.class, this::mapError);
    }

    // 又拍云通过服务端代理下载，不返回直链。
    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        return Mono.just(new StorageSource(null, null, true));
    }

    // 通过查询空间用量检测连接状态。
    @Override
    public Mono<StorageTestResult> testConnection() {
        return Mono.fromCallable(() -> {
                    client.getBucketUsage();
                    return new StorageTestResult(true, "存储连接正常。", Instant.now());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.just(new StorageTestResult(
                        false, "存储连接失败：" + safeMessage(error), Instant.now())));
    }

    // 无长期持有的外部连接需要关闭。
    @Override
    public void close() {}

    // 返回策略配置的存储桶名称。
    private String bucket() {
        return policy.getSpec().getBucketName();
    }

    // 同步查询文件信息并转换为对象元数据。
    private StorageObject statBlocking(String key) throws IOException, UpException {
        var info = fileInfo(key);
        if (info == null) {
            throw StorageDriverErrors.notFound();
        }
        var size = parseLong(info.get("size"));
        var lastModified = parseDate(info.get("last-modified"));
        var etag = info.getOrDefault("etag", info.get("file-hash"));
        return new StorageObject(key, size, lastModified, etag);
    }

    // 在客户端锁内查询，避免并发调用冲突。
    private Map<String, String> fileInfo(String key) throws IOException, UpException {
        synchronized (client) {
            return client.getFileInfo(key);
        }
    }

    // 解析长整型字段，缺失或非法时返回零。
    private static long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return 0;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 解析 RFC 1123 时间，格式错误时返回空。
    private static Instant parseDate(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // 按又拍云规则计算表单上传签名。
    private static String sign(String operator, String password, List<String> elements) {
        try {
            var passwordHash = MessageDigest.getInstance("MD5").digest(password.getBytes(StandardCharsets.UTF_8));
            var passwordHex = HexFormat.of().formatHex(passwordHash);
            var mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(passwordHex.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            var value = String.join("&", elements);
            var digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "UPYUN " + operator + ":" + Base64.getEncoder().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("无法生成又拍云上传签名", e);
        }
    }

    // 将又拍云异常映射为存储服务错误。
    private ApiException mapError(UpException error) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败。", error);
    }

    // 将本地读取异常映射为存储读取错误。
    private ApiException ioError(IOException error) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储读取失败。", error);
    }


    // 提取可安全展示的错误信息。
    private static String safeMessage(Throwable error) {
        return StringUtils.hasText(error.getMessage()) ? error.getMessage() : "存储服务不可用。";
    }

}
