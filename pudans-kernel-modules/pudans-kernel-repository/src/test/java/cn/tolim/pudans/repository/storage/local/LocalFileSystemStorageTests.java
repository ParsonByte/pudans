package cn.tolim.pudans.repository.storage.local;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryException;
import cn.tolim.pudans.repository.storage.StorageException;
import cn.tolim.pudans.repository.storage.StorageObjectNotFoundException;
import cn.tolim.pudans.repository.storage.StorageObjectStat;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.StoredObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileSystemStorageTests {

    @TempDir
    Path tempDir;

    private LocalFileSystemStorage storage;

    @BeforeEach
    void setUp() {
        RepositoryProperties.Local properties = new RepositoryProperties.Local();
        properties.setBaseDirectory(tempDir.resolve("repository").toString());
        storage = new LocalFileSystemStorage(properties);
    }

    @Test
    void writesReadsStatsAndDeletesAnObject() throws IOException {
        byte[] content = "hello repository".getBytes(StandardCharsets.UTF_8);

        StoredObject stored = storage.put("tenant/app/2026/01/file.txt",
                new ByteArrayInputStream(content), content.length, "text/plain", Map.of("color", "blue"));

        assertThat(stored.objectKey()).isEqualTo("tenant/app/2026/01/file.txt");
        assertThat(stored.size()).isEqualTo(content.length);
        assertThat(storage.exists(stored.objectKey())).isTrue();
        assertThat(storage.provider()).isEqualTo(StorageProviderType.LOCAL);

        StorageObjectStat stat = storage.stat(stored.objectKey());
        assertThat(stat.size()).isEqualTo(content.length);

        try (InputStream input = storage.open(stored.objectKey())) {
            assertThat(input.readAllBytes()).isEqualTo(content);
        }

        storage.delete(stored.objectKey());

        assertThat(storage.exists(stored.objectKey())).isFalse();
        assertThatThrownBy(() -> storage.open(stored.objectKey()))
                .isInstanceOf(StorageObjectNotFoundException.class);
    }

    @Test
    void reportsDeclaredSizeMismatch() {
        byte[] content = "abc".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> storage.put("tenant/file.bin", new ByteArrayInputStream(content),
                100, "application/octet-stream", Map.of()))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("declared size");
        assertThat(Files.exists(tempDir.resolve("repository").resolve("tenant/file.bin"))).isFalse();
    }

    @Test
    void rejectsPathTraversalKeys() {
        assertThatThrownBy(() -> storage.resolve("../escape.txt"))
                .isInstanceOf(RepositoryException.class)
                .hasMessageContaining("escape");
        assertThatThrownBy(() -> storage.resolve("/absolute.txt"))
                .isInstanceOf(RepositoryException.class);
        assertThatThrownBy(() -> storage.resolve("tenant/../../escape.txt"))
                .isInstanceOf(RepositoryException.class);
        assertThatThrownBy(() -> storage.resolve("tenant\\file.txt"))
                .isInstanceOf(RepositoryException.class);
    }

    @Test
    void doesNotIssuePresignedUrls() {
        assertThat(storage.directDownload()).isFalse();
        assertThatThrownBy(() -> storage.presignedGetUrl("tenant/file.txt", Duration.ofMinutes(5)))
                .isInstanceOf(StorageException.class)
                .hasMessageContaining("presigned");
    }
}