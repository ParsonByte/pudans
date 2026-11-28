package cn.tolim.pudans.repository.storage;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ObjectStorageRegistryTests {

    @Test
    void resolvesDefaultAndConfiguredProviders() {
        FakeStorage local = new FakeStorage(StorageProviderType.LOCAL);
        FakeStorage s3 = new FakeStorage(StorageProviderType.S3);
        Map<StorageProviderType, ObjectStorage> storages = new EnumMap<>(StorageProviderType.class);
        storages.put(StorageProviderType.LOCAL, local);
        storages.put(StorageProviderType.S3, s3);

        ObjectStorageRegistry registry = ObjectStorageRegistry.of(storages, StorageProviderType.LOCAL);

        assertThat(registry.defaultProvider()).isEqualTo(StorageProviderType.LOCAL);
        assertThat(registry.defaultStorage()).isSameAs(local);
        assertThat(registry.require(StorageProviderType.S3)).isSameAs(s3);
        assertThat(registry.isConfigured(StorageProviderType.MINIO)).isFalse();
        assertThat(registry.configuredProviders())
                .containsExactlyInAnyOrder(StorageProviderType.LOCAL, StorageProviderType.S3);
    }

    @Test
    void rejectsMissingDefaultProvider() {
        Map<StorageProviderType, ObjectStorage> storages = new EnumMap<>(StorageProviderType.class);
        storages.put(StorageProviderType.LOCAL, new FakeStorage(StorageProviderType.LOCAL));

        assertThatThrownBy(() -> ObjectStorageRegistry.of(storages, StorageProviderType.S3))
                .isInstanceOf(StorageNotConfiguredException.class);
        assertThatThrownBy(() -> ObjectStorageRegistry.of(Map.of(), StorageProviderType.LOCAL))
                .isInstanceOf(StorageNotConfiguredException.class);
    }

    @Test
    void requiresConfiguredProviderOnUse() {
        ObjectStorageRegistry registry = ObjectStorageRegistry.of(
                Map.of(StorageProviderType.LOCAL, new FakeStorage(StorageProviderType.LOCAL)),
                StorageProviderType.LOCAL);

        assertThatThrownBy(() -> registry.require(StorageProviderType.MINIO))
                .isInstanceOf(StorageNotConfiguredException.class)
                .hasMessageContaining("minio");
    }

    @Test
    void parsesProviderCodes() {
        assertThat(StorageProviderType.fromCode("aliyun_oss")).isEqualTo(StorageProviderType.ALIYUN_OSS);
        assertThat(StorageProviderType.fromCode("S3")).isEqualTo(StorageProviderType.S3);
        assertThat(StorageProviderType.fromCode("local").code()).isEqualTo("local");
        assertThatThrownBy(() -> StorageProviderType.fromCode("unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static final class FakeStorage implements ObjectStorage {

        private final StorageProviderType provider;

        private FakeStorage(StorageProviderType provider) {
            this.provider = provider;
        }

        @Override
        public StorageProviderType provider() {
            return provider;
        }

        @Override
        public StoredObject put(String objectKey, InputStream content, long size, String contentType,
                                Map<String, String> metadata) {
            return new StoredObject(objectKey, size, null);
        }

        @Override
        public InputStream open(String objectKey) {
            return InputStream.nullInputStream();
        }

        @Override
        public StorageObjectStat stat(String objectKey) {
            return new StorageObjectStat(objectKey, 0, null, null);
        }

        @Override
        public void delete(String objectKey) {
        }

        @Override
        public URI presignedGetUrl(String objectKey, Duration timeToLive) {
            return URI.create("https://example.test/" + objectKey);
        }
    }
}