package cn.tolim.pudans.core.storage.driver.local;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicy;
import cn.tolim.pudans.contract.core.extension.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ByteRange;
import cn.tolim.pudans.core.storage.driver.StorageCapability;
import cn.tolim.pudans.core.storage.driver.StorageDriver;
import cn.tolim.pudans.core.storage.driver.StorageObject;
import cn.tolim.pudans.core.storage.driver.StoragePutRequest;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.core.storage.driver.StorageReadResult;
import cn.tolim.pudans.core.storage.driver.StorageSource;
import cn.tolim.pudans.core.storage.driver.StorageSourceOptions;
import cn.tolim.pudans.core.storage.driver.StorageTestResult;
import cn.tolim.pudans.infra.exception.ApiException;

public class LocalStorageDriver implements StorageDriver {

    private static final int READ_BUFFER_SIZE = 64 * 1024;

    private final StoragePolicy policy;

    private final Path root;

    public LocalStorageDriver(StoragePolicy policy, Path root) {
        this.policy = policy;
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public StoragePolicyType type() {
        return StoragePolicyType.LOCAL;
    }

    @Override
    public Set<StorageCapability> capabilities() {
        return Set.of(StorageCapability.RANGE_READ, StorageCapability.PROXY_REQUIRED);
    }

    @Override
    public Mono<StorageObject> put(StoragePutRequest request) {
        return Mono.fromCallable(() -> {
                    var target = resolve(request.key());
                    if (!request.overwrite() && Files.exists(target)) {
                        throw new ApiException(HttpStatus.CONFLICT, "storage-object-exists", "文件已存在。");
                    }
                    Files.createDirectories(target.getParent());
                    return Files.createTempFile(target.getParent(), ".upload-", ".tmp");
                })
                .flatMap(temp -> DataBufferUtils.write(
                                request.body(),
                                temp,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)
                        .then(Mono.fromCallable(() -> {
                            var size = Files.size(temp);
                            if (policy.getSpec().getMaxSize() > 0 && size > policy.getSpec().getMaxSize()) {
                                Files.deleteIfExists(temp);
                                throw new ApiException(
                                        HttpStatus.PAYLOAD_TOO_LARGE,
                                        "storage-file-too-large",
                                        "文件大小超过策略上限。");
                            }
                            var target = resolve(request.key());
                            Files.move(
                                    temp,
                                    target,
                                    java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            return stat(target, request.key());
                        }))
                        .doOnError(error -> deleteQuietly(temp))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @Override
    public Mono<StorageReadResult> get(String key, StorageReadOptions options) {
        return Mono.fromCallable(() -> {
                    var target = resolve(key);
                    var attributes = Files.readAttributes(target, BasicFileAttributes.class);
                    var total = attributes.size();
                    var range = options == null ? null : options.range();
                    var start = range == null ? 0 : Math.max(0, range.start());
                    var end = range == null ? total - 1 : Math.min(range.end(), total - 1);
                    if (total > 0 && start >= total) {
                        throw new ApiException(
                                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE,
                                "storage-range-invalid",
                                "请求的字节范围无效。");
                    }
                    var length = total == 0 ? 0 : end - start + 1;
                    var object = new StorageObject(
                            key, total, attributes.lastModifiedTime().toInstant(), etag(total, attributes));
                    var body = readRange(target, start, length);
                    return new StorageReadResult(object, start, end, length, body);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(NoSuchFileException.class, error -> notFound());
    }

    @Override
    public Mono<StorageObject> stat(String key) {
        return Mono.fromCallable(() -> {
                    var target = resolve(key);
                    return stat(target, key);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(NoSuchFileException.class, error -> notFound());
    }

    @Override
    public Mono<Void> delete(Collection<String> keys) {
        return Flux.fromIterable(keys)
                .concatMap(key -> Mono.fromRunnable(() -> deleteQuietly(resolve(key)))
                        .subscribeOn(Schedulers.boundedElastic()))
                .then();
    }

    @Override
    public Mono<StorageSource> source(String key, StorageSourceOptions options) {
        return Mono.just(new StorageSource(null, null, true));
    }

    @Override
    public Mono<StorageTestResult> testConnection() {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(root);
                    var probe = Files.createTempFile(root, ".probe-", ".tmp");
                    Files.deleteIfExists(probe);
                    return new StorageTestResult(true, "本地存储可写。", Instant.now());
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(error -> Mono.just(
                        new StorageTestResult(false, "本地存储不可写：" + error.getMessage(), Instant.now())));
    }

    private Path resolve(String key) {
        if (!StringUtils.hasText(key)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-key-invalid", "存储路径不能为空。");
        }
        var target = root.resolve(key).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "storage-key-invalid", "存储路径不合法。");
        }
        return target;
    }

    private StorageObject stat(Path target, String key) throws IOException {
        var attributes = Files.readAttributes(target, BasicFileAttributes.class);
        return new StorageObject(key, attributes.size(), attributes.lastModifiedTime().toInstant(), etag(attributes.size(), attributes));
    }

    private static String etag(long size, BasicFileAttributes attributes) {
        return size + "-" + attributes.lastModifiedTime().toMillis();
    }

    private Flux<DataBuffer> readRange(Path path, long start, long length) {
        if (length <= 0) {
            return Flux.empty();
        }
        return Flux.<DataBuffer, ReadState>generate(
                        () -> openState(path, start, length),
                        (state, sink) -> {
                            if (state.remaining().get() <= 0) {
                                sink.complete();
                                return state;
                            }
                            var buffer = ByteBuffer.allocate(
                                    (int) Math.min((long) READ_BUFFER_SIZE, state.remaining().get()));
                            try {
                                var read = state.channel().read(buffer, state.position().get());
                                if (read < 0) {
                                    sink.complete();
                                    return state;
                                }
                                state.position().addAndGet(read);
                                state.remaining().addAndGet(-read);
                                buffer.flip();
                                sink.next(DefaultDataBufferFactory.sharedInstance.wrap(buffer));
                            } catch (IOException e) {
                                sink.error(e);
                            }
                            return state;
                        },
                        state -> {
                            try {
                                state.channel().close();
                            } catch (IOException ignored) {
                            }
                        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static ReadState openState(Path path, long start, long length) {
        try {
            return new ReadState(FileChannel.open(path, StandardOpenOption.READ), new AtomicLong(start), new AtomicLong(length));
        } catch (IOException e) {
            throw new IllegalStateException("无法打开文件 " + path, e);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "storage-object-not-found", "文件不存在。");
    }

    private record ReadState(FileChannel channel, AtomicLong position, AtomicLong remaining) {}
}
