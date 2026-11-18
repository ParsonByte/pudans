package cn.tolim.pudans.core.storage.driver.upyun;

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
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
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

    public UpyunStorageDriver(
            StoragePolicy policy, UpYun client, String operator, String password, Path tempDirectory) {
        this.policy = policy;
        this.client = client;
        this.operator = operator;
        this.password = password;
        this.tempDirectory = tempDirectory;
    }

    @Override
    public StoragePolicyType type() {
        return StoragePolicyType.UPYUN;
    }

    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(StorageCapability.DIRECT_UPLOAD, StorageCapability.RANGE_READ);
    }

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

    @Override
    public Mono<StorageObject> put(StoragePutRequest request) {
        return Mono.fromCallable(() -> client.getFileInfo(request.key()))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.empty())
                .flatMap(info -> request.overwrite() ? Mono.empty() : Mono.error(conflict()))
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

    @Override
    public Mono<StorageReadResult> get(String key, StorageReadOptions options) {
        var range = options == null ? null : options.range();
        return Mono.fromCallable(() -> {
                    var total = statBlocking(key).size();
                    var temp = Files.createTempFile(tempDirectory, ".download-", ".tmp");
                    var success = client.readFile(key, temp.toFile());
                    if (!success) {
                        StorageIo.deleteQuietly(temp);
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
                        input.skip(start);
                    }
                    var body = StorageIo.read(input, length)
                            .doFinally(signal -> StorageIo.deleteQuietly(temp));
                    return new StorageReadResult(
                            statBlocking(key), start, end, length, body);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(UpException.class, this::mapError);
    }

    @Override
    public Mono<StorageObject> stat(String key) {
        return Mono.fromCallable(() -> statBlocking(key))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(UpException.class, this::mapError);
    }

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

    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        return Mono.just(new StorageSource(null, null, true));
    }

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

    @Override
    public void close() {}

    private String bucket() {
        return policy.getSpec().getBucketName();
    }

    private StorageObject statBlocking(String key) throws IOException, UpException {
        var info = client.getFileInfo(key);
        var size = parseLong(info.get("size"));
        var lastModified = parseDate(info.get("last-modified"));
        var etag = info.getOrDefault("etag", info.get("file-hash"));
        return new StorageObject(key, size, lastModified, etag);
    }

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

    private ApiException mapError(UpException error) {
        return new ApiException(
                HttpStatus.BAD_GATEWAY, "storage-driver-error", "存储服务请求失败。", error);
    }

    private static String safeMessage(Throwable error) {
        return StringUtils.hasText(error.getMessage()) ? error.getMessage() : "存储服务不可用。";
    }

    private static ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, "storage-object-exists", "文件已存在。");
    }
}
