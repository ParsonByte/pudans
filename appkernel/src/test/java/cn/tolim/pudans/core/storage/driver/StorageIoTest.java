package cn.tolim.pudans.core.storage.driver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class StorageIoTest {

    @TempDir
    Path tempDir;

    @Test
    void limitRejectsContentAboveTheDeclaredSize() {
        var body = Flux.<DataBuffer>just(
                buffer("abc"),
                buffer("def"));

        StepVerifier.create(StorageIo.limit(body, 5))
                .expectNextMatches(data -> data.readableByteCount() == 3)
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOfSatisfying(ApiException.class,
                                api -> assertThat(api.getCode()).isEqualTo("storage-file-too-large")))
                .verify();
    }

    @Test
    void spoolDeletesTheTemporaryFileWhenContentExceedsTheLimit() throws Exception {
        assertThatThrownBy(() -> StorageIo.spool(Flux.just(buffer("abcdefgh")), tempDir, 4).block())
                .isInstanceOf(ApiException.class);
        try (var files = Files.list(tempDir)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void spoolWritesContentWithinTheLimit() throws Exception {
        var path = StorageIo.spool(Flux.just(buffer("data")), tempDir, 4).block();

        assertThat(path).isNotNull();
        assertThat(Files.readString(path)).isEqualTo("data");
        StorageIo.deleteQuietly(path);
    }

    @Test
    void ensureUsableSpaceAcceptsSmallWrites() {
        StorageIo.ensureUsableSpace(tempDir, 1);
    }

    // 构造指定文本的数据缓冲区。
    private static DataBuffer buffer(String value) {
        return DefaultDataBufferFactory.sharedInstance.wrap(value.getBytes(StandardCharsets.UTF_8));
    }
}
