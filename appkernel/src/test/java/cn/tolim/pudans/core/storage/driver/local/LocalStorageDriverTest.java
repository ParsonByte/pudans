package cn.tolim.pudans.core.storage.driver.local;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import cn.tolim.pudans.contract.storage.StoragePolicy;
import cn.tolim.pudans.contract.storage.StoragePolicyType;
import cn.tolim.pudans.core.storage.driver.ByteRange;
import cn.tolim.pudans.core.storage.driver.StoragePutRequest;
import cn.tolim.pudans.core.storage.driver.StorageReadOptions;
import cn.tolim.pudans.infra.exception.ApiException;
import reactor.core.publisher.Flux;

class LocalStorageDriverTest {

    @TempDir
    Path workDir;

    private LocalStorageDriver driver;

    @BeforeEach
    void setUp() {
        var policy = new StoragePolicy();
        policy.getSpec().setDisplayName("local");
        policy.getSpec().setType(StoragePolicyType.LOCAL);
        policy.getSpec().setMaxSize(1024);
        driver = new LocalStorageDriver(policy, workDir);
    }

    @Test
    void storesReadsAndDeletesAFile() {
        var content = "hello world".getBytes(StandardCharsets.UTF_8);

        var stored = driver.put(request("uploads/a.txt", content, false)).block();

        assertThat(stored).isNotNull();
        assertThat(stored.size()).isEqualTo(content.length);
        assertThat(Files.exists(workDir.resolve("uploads/a.txt"))).isTrue();
        assertThat(driver.stat("uploads/a.txt").block().size()).isEqualTo(content.length);

        var read = driver.get("uploads/a.txt", StorageReadOptions.full()).block();
        assertThat(read).isNotNull();
        assertThat(read.contentLength()).isEqualTo(content.length);
        assertThat(readBytes(read.body())).isEqualTo(content);

        driver.delete(List.of("uploads/a.txt")).block();
        assertThatThrownBy(() -> driver.stat("uploads/a.txt").block()).isInstanceOf(ApiException.class);
    }

    @Test
    void readsARange() {
        var content = "hello world".getBytes(StandardCharsets.UTF_8);
        driver.put(request("uploads/range.txt", content, false)).block();

        var read = driver.get("uploads/range.txt", new StorageReadOptions(new ByteRange(6, 10))).block();

        assertThat(read).isNotNull();
        assertThat(read.rangeStart()).isEqualTo(6);
        assertThat(read.rangeEnd()).isEqualTo(10);
        assertThat(read.contentLength()).isEqualTo(5);
        assertThat(new String(readBytes(read.body()), StandardCharsets.UTF_8)).isEqualTo("world");
    }

    @Test
    void rejectsPathTraversal() {
        assertThatThrownBy(() -> driver.put(request("../escape.txt", "x".getBytes(), false)).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("storage-key-invalid"));
    }

    @Test
    void rejectsOverwriteWhenDisabled() {
        driver.put(request("uploads/a.txt", "one".getBytes(), false)).block();

        assertThatThrownBy(() -> driver.put(request("uploads/a.txt", "two".getBytes(), false)).block())
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).getCode()).isEqualTo("storage-object-exists"));
    }

    @Test
    void testConnectionSucceeds() {
        var result = driver.testConnection().block();

        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(Files.isDirectory(workDir)).isTrue();
    }

    private static StoragePutRequest request(String key, byte[] content, boolean overwrite) {
        var buffer = DefaultDataBufferFactory.sharedInstance.wrap(ByteBuffer.wrap(content));
        return new StoragePutRequest(key, content.length, "text/plain", Flux.just(buffer), overwrite);
    }

    private static byte[] readBytes(Flux<DataBuffer> body) {
        var buffer = DataBufferUtils.join(body).block();
        assertThat(buffer).isNotNull();
        var bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        DataBufferUtils.release(buffer);
        return bytes;
    }
}
