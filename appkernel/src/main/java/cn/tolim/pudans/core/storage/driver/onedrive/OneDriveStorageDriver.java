package cn.tolim.pudans.core.storage.driver.onedrive;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
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
import cn.tolim.pudans.core.infra.exception.ApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public class OneDriveStorageDriver implements StorageDriver {

    private static final String GRAPH = "https://graph.microsoft.com/v1.0";

    private static final long SIMPLE_UPLOAD_LIMIT = 4L * 1024L * 1024L;

    private static final int UPLOAD_CHUNK_SIZE = 10 * 1024 * 1024;

    private static final Duration DEFAULT_SOURCE_TTL = Duration.ofMinutes(15);

    private final StoragePolicy policy;

    private final OneDriveTokenClient tokenClient;

    private final String driveId;

    private final Path tempDirectory;

    private final WebClient webClient = WebClient.builder().build();

    public OneDriveStorageDriver(
            StoragePolicy policy, StoragePolicy.Credentials credentials, Path tempDirectory) {
        this.policy = policy;
        var spec = policy.getSpec();
        var resolved = credentials == null ? new StoragePolicy.Credentials() : credentials;
        var settings = spec.getSettings();
        this.tokenClient = new OneDriveTokenClient(
                policy, resolved.getClientSecret(), resolved.getToken(), webClient);
        this.driveId = settings == null ? null : settings.getDriveId();
        this.tempDirectory = tempDirectory;
    }

    @Override
    public StoragePolicyType type() {
        return StoragePolicyType.ONEDRIVE;
    }

    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(StorageCapability.PRESIGNED_SOURCE, StorageCapability.RANGE_READ);
    }

    @Override
    public Mono<StorageObject> put(StoragePutRequest request) {
        return StorageIo.spool(request.body(), tempDirectory)
                .flatMap(path -> Mono.fromCallable(() -> Files.size(path))
                        .subscribeOn(Schedulers.boundedElastic())
                        .flatMap(size -> size <= SIMPLE_UPLOAD_LIMIT
                                ? simpleUpload(request, path)
                                : sessionUpload(request, path, size))
                        .doFinally(signal -> StorageIo.deleteQuietly(path)))
                .then(stat(request.key()))
                .onErrorMap(WebClientResponseException.class, this::mapError);
    }

    @Override
    public Mono<StorageReadResult> get(String key, StorageReadOptions options) {
        var range = options == null ? null : options.range();
        return downloadUrl(key).flatMap(url -> webClient
                .get()
                .uri(url)
                .headers(headers -> {
                    if (range != null) {
                        headers.set(HttpHeaders.RANGE, "bytes=" + range.start() + "-" + range.end());
                    }
                })
                .retrieve()
                .onStatus(status -> status.value() == 404, response -> Mono.error(notFound()))
                .onStatus(HttpStatusCode::isError, this::error)
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
                }));
    }

    @Override
    public Mono<StorageObject> stat(String key) {
        return tokenClient.accessToken()
                .flatMap(value -> webClient
                        .get()
                        .uri(item(key) + "?$select=size,eTag,lastModifiedDateTime")
                        .headers(headers -> headers.setBearerAuth(value))
                        .retrieve()
                        .onStatus(status -> status.value() == 404, response -> Mono.error(notFound()))
                        .onStatus(HttpStatusCode::isError, this::error)
                        .bodyToMono(String.class)
                        .map(body -> toObject(key, body)))
                .onErrorMap(WebClientResponseException.class, this::mapError);
    }

    @Override
    public Mono<Void> delete(Collection<String> keys) {
        return tokenClient.accessToken()
                .flatMapMany(value -> Flux.fromIterable(keys)
                        .concatMap(key -> webClient
                                .delete()
                                .uri(item(key))
                                .headers(headers -> headers.setBearerAuth(value))
                                .retrieve()
                                .onStatus(status -> status.value() == 404, response -> Mono.empty())
                                .onStatus(HttpStatusCode::isError, this::error)
                                .toBodilessEntity()))
                .then()
                .onErrorMap(WebClientResponseException.class, this::mapError);
    }

    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        return downloadUrl(key).map(url -> new StorageSource(
                URI.create(url), Instant.now().plus(DEFAULT_SOURCE_TTL), false));
    }

    @Override
    public Mono<StorageTestResult> testConnection() {
        return tokenClient.accessToken()
                .flatMap(value -> webClient
                        .get()
                        .uri(GRAPH + drive())
                        .headers(headers -> headers.setBearerAuth(value))
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::error)
                        .toBodilessEntity()
                        .thenReturn(new StorageTestResult(true, "存储连接正常。", Instant.now())))
                .onErrorResume(error -> Mono.just(new StorageTestResult(
                        false, "存储连接失败：" + safeMessage(error), Instant.now())));
    }

    @Override
    public void close() {}

    private Mono<StorageObject> simpleUpload(StoragePutRequest request, Path path) {
        return tokenClient.accessToken().flatMap(value -> Mono.fromCallable(() -> Files.readAllBytes(path))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> webClient
                        .put()
                        .uri(item(request.key()) + ":/content")
                        .headers(headers -> headers.setBearerAuth(value))
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .bodyValue(bytes)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::error)
                        .toBodilessEntity()
                        .then(stat(request.key()))));
    }

    private Mono<StorageObject> sessionUpload(StoragePutRequest request, Path path, long size) {
        return tokenClient.accessToken()
                .flatMap(value -> createUploadSession(request.key(), value))
                .flatMap(uploadUrl -> uploadChunks(uploadUrl, path, size, 0L))
                .then(stat(request.key()));
    }

    private Mono<String> createUploadSession(String key, String accessToken) {
        return webClient
                .post()
                .uri(item(key) + ":/createUploadSession")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("item", Map.of("@microsoft.graph.conflictBehavior", "replace")))
                .retrieve()
                .onStatus(HttpStatusCode::isError, this::error)
                .bodyToMono(String.class)
                .map(body -> JsonMapper.shared().readTree(body).path("uploadUrl").asString())
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(new ApiException(
                        HttpStatus.BAD_GATEWAY, "storage-driver-error", "无法创建 OneDrive 上传会话。")));
    }

    private Mono<Void> uploadChunks(String uploadUrl, Path path, long size, long offset) {
        if (offset >= size) {
            return Mono.empty();
        }
        var length = (int) Math.min(UPLOAD_CHUNK_SIZE, size - offset);
        var end = offset + length - 1;
        return Mono.fromCallable(() -> {
                    try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
                        var buffer = ByteBuffer.allocate(length);
                        var read = channel.read(buffer, offset);
                        return read <= 0 ? new byte[0] : java.util.Arrays.copyOf(buffer.array(), read);
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> webClient
                        .put()
                        .uri(uploadUrl)
                        .header(HttpHeaders.CONTENT_RANGE, "bytes " + offset + "-" + end + "/" + size)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .bodyValue(bytes)
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, this::error)
                        .toBodilessEntity())
                .then(Mono.defer(() -> uploadChunks(uploadUrl, path, size, offset + length)));
    }

    private Mono<String> downloadUrl(String key) {
        return tokenClient.accessToken()
                .flatMap(value -> webClient
                        .get()
                        .uri(item(key) + "?$select=@microsoft.graph.downloadUrl")
                        .headers(headers -> headers.setBearerAuth(value))
                        .retrieve()
                        .onStatus(status -> status.value() == 404, response -> Mono.error(notFound()))
                        .onStatus(HttpStatusCode::isError, this::error)
                        .bodyToMono(String.class)
                        .map(body -> JsonMapper.shared()
                                .readTree(body)
                                .path("@microsoft.graph.downloadUrl")
                                .asString()))
                .filter(StringUtils::hasText)
                .switchIfEmpty(Mono.error(notFound()));
    }

    private StorageObject toObject(String key, String body) {
        var json = JsonMapper.shared().readTree(body);
        return new StorageObject(key, json.path("size").asLong(0), parseInstant(json), json.path("eTag").asString());
    }

    private static Instant parseInstant(JsonNode json) {
        var value = json.path("lastModifiedDateTime").asString();
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException e) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }

    private String item(String key) {
        return GRAPH + drive() + "/root:/" + StorageIo.encodePath(key);
    }

    private String drive() {
        return StringUtils.hasText(driveId) ? "/drives/" + driveId : "/me/drive";
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

    private Mono<ApiException> error(ClientResponse response) {
        var status = response.statusCode();
        if (status.value() == 404) {
            return Mono.just(notFound());
        }
        if (status.value() == 401 || status.value() == 403) {
            return Mono.just(forbidden());
        }
        return Mono.just(new ApiException(
                HttpStatus.BAD_GATEWAY,
                "storage-driver-error",
                "存储服务请求失败（" + status.value() + "）。"));
    }

    private ApiException mapError(WebClientResponseException error) {
        if (error.getStatusCode().value() == 404) {
            return notFound();
        }
        if (error.getStatusCode().value() == 401 || error.getStatusCode().value() == 403) {
            return forbidden();
        }
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "storage-driver-error",
                "存储服务请求失败（" + error.getStatusCode().value() + "）。",
                error);
    }

    private static String safeMessage(Throwable error) {
        return error instanceof ApiException api && api.getReason() != null
                ? api.getReason()
                : "存储服务不可用。";
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "storage-object-not-found", "文件不存在。");
    }

    private static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, "storage-access-denied", "存储访问被拒绝。");
    }
}
