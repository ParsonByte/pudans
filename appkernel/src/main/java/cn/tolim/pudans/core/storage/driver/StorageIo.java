package cn.tolim.pudans.core.storage.driver;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpStatus;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class StorageIo {

    private static final int BUFFER_SIZE = 64 * 1024;

    private static final long SPACE_RESERVE_BYTES = 64L * 1024 * 1024;

    private static final char[] UPPER_HEX = "0123456789ABCDEF".toCharArray();

    private StorageIo() {}

    // 将数据流暂存为目录中的临时文件。
    public static Mono<Path> spool(Flux<DataBuffer> body, Path directory) {
        return spool(body, directory, 0);
    }

    // 暂存数据流并限制最大字节数，同时预留磁盘安全水位。
    public static Mono<Path> spool(Flux<DataBuffer> body, Path directory, long maxBytes) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(directory);
                    if (maxBytes > 0) {
                        ensureUsableSpace(directory, maxBytes);
                    }
                    return Files.createTempFile(directory, ".upload-", ".tmp");
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> DataBufferUtils.write(
                                limit(body, maxBytes),
                                path,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)
                        .thenReturn(path)
                        .doOnError(error -> deleteQuietly(path))
                        .doOnCancel(() -> deleteQuietly(path))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    // 累计字节超过上限时终止数据流并释放缓冲。
    public static Flux<DataBuffer> limit(Flux<DataBuffer> body, long maxBytes) {
        if (maxBytes <= 0) {
            return body;
        }
        var written = new AtomicLong();
        return body.concatMap(buffer -> {
            var total = written.addAndGet(buffer.readableByteCount());
            if (total > maxBytes) {
                DataBufferUtils.release(buffer);
                return Flux.error(new ApiException(
                        HttpStatus.PAYLOAD_TOO_LARGE, "storage-file-too-large", "上传内容超过声明大小。"));
            }
            return Flux.just(buffer);
        });
    }

    // 写入前检查目录所在文件系统的可用空间。
    public static void ensureUsableSpace(Path directory, long requiredBytes) {
        try {
            Files.createDirectories(directory);
            var usable = Files.getFileStore(directory).getUsableSpace();
            if (requiredBytes > Math.max(0L, usable - SPACE_RESERVE_BYTES)) {
                throw new ApiException(
                        HttpStatus.INSUFFICIENT_STORAGE,
                        "storage-temp-space-insufficient",
                        "临时存储空间不足，无法写入文件。");
            }
        } catch (IOException e) {
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "storage-temp-space-unknown",
                    "无法检测临时存储空间。");
        }
    }

    // 从输入流按剩余长度生成数据缓冲区。
    public static Flux<DataBuffer> read(InputStream input, long length) {
        return Flux.<DataBuffer, ReadState>generate(
                        () -> new ReadState(input, new AtomicLong(length)),
                        (state, sink) -> {
                            if (state.remaining().get() == 0) {
                                sink.complete();
                                return state;
                            }
                            try {
                                var size = state.remaining().get() < 0
                                        ? BUFFER_SIZE
                                        : (int) Math.min((long) BUFFER_SIZE, state.remaining().get());
                                var buffer = ByteBuffer.allocate(size);
                                var read = state.input().read(buffer.array(), 0, size);
                                if (read < 0) {
                                    sink.complete();
                                    return state;
                                }
                                state.remaining().addAndGet(-read);
                                buffer.limit(read);
                                sink.next(DefaultDataBufferFactory.sharedInstance.wrap(buffer));
                            } catch (IOException e) {
                                sink.error(e);
                            }
                            return state;
                        },
                        state -> closeQuietly(state.input()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    // 按路径段编码对象键，保留分隔斜杠。
    public static String encodePath(String path) {
        if (path == null) {
            return "";
        }
        var builder = new StringBuilder();
        var segments = path.split("/", -1);
        for (var index = 0; index < segments.length; index++) {
            if (index > 0) {
                builder.append('/');
            }
            builder.append(encodeSegment(segments[index]));
        }
        return builder.toString();
    }

    // 删除文件并忽略不存在的路径。
    public static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

    // 读取普通文件大小，失败按零处理。
    public static long sizeOf(Path path) {
        if (path == null) {
            return 0;
        }
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0;
        } catch (IOException error) {
            return 0;
        }
    }

    // 对单个路径段做百分号编码。
    private static String encodeSegment(String value) {
        var bytes = value.getBytes(StandardCharsets.UTF_8);
        var builder = new StringBuilder(bytes.length * 3);
        for (var b : bytes) {
            var c = b & 0xff;
            if ((c >= 'A' && c <= 'Z')
                    || (c >= 'a' && c <= 'z')
                    || (c >= '0' && c <= '9')
                    || c == '-'
                    || c == '_'
                    || c == '.'
                    || c == '~') {
                builder.append((char) c);
            } else {
                builder.append('%').append(UPPER_HEX[c >> 4]).append(UPPER_HEX[c & 0x0f]);
            }
        }
        return builder.toString();
    }

    // 关闭输入流并忽略关闭异常。
    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    private record ReadState(InputStream input, AtomicLong remaining) {}
}
