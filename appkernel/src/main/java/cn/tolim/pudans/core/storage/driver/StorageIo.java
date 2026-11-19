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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

public final class StorageIo {

    private static final int BUFFER_SIZE = 64 * 1024;

    private static final char[] UPPER_HEX = "0123456789ABCDEF".toCharArray();

    private StorageIo() {}

    public static Mono<Path> spool(Flux<DataBuffer> body, Path directory) {
        return Mono.fromCallable(() -> {
                    Files.createDirectories(directory);
                    return Files.createTempFile(directory, ".upload-", ".tmp");
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> DataBufferUtils.write(
                                body,
                                path,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING,
                                StandardOpenOption.WRITE)
                        .thenReturn(path)
                        .doOnError(error -> deleteQuietly(path))
                        .doOnCancel(() -> deleteQuietly(path))
                        .subscribeOn(Schedulers.boundedElastic()));
    }

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

    public static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }

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

    private static void closeQuietly(InputStream input) {
        try {
            input.close();
        } catch (IOException ignored) {
        }
    }

    private record ReadState(InputStream input, AtomicLong remaining) {}
}
