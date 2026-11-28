package cn.tolim.pudans.repository.storage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

public final class ObjectStorageRegistry implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageRegistry.class);

    private final Map<StorageProviderType, ObjectStorage> storages;
    private final StorageProviderType defaultProvider;

    private ObjectStorageRegistry(Map<StorageProviderType, ObjectStorage> storages,
                                  StorageProviderType defaultProvider) {
        this.storages = Collections.unmodifiableMap(new EnumMap<>(storages));
        this.defaultProvider = defaultProvider;
    }

    // Build a registry from configured providers
    public static ObjectStorageRegistry of(Map<StorageProviderType, ObjectStorage> storages,
                                           StorageProviderType defaultProvider) {
        if (storages == null || storages.isEmpty()) {
            throw new StorageNotConfiguredException(defaultProvider);
        }
        if (!storages.containsKey(defaultProvider)) {
            throw new StorageNotConfiguredException(defaultProvider);
        }
        return new ObjectStorageRegistry(storages, defaultProvider);
    }

    public StorageProviderType defaultProvider() {
        return defaultProvider;
    }

    public ObjectStorage defaultStorage() {
        return require(defaultProvider);
    }

    public ObjectStorage require(StorageProviderType provider) {
        ObjectStorage storage = storages.get(provider);
        if (storage == null) {
            throw new StorageNotConfiguredException(provider);
        }
        return storage;
    }

    public boolean isConfigured(StorageProviderType provider) {
        return storages.containsKey(provider);
    }

    public Set<StorageProviderType> configuredProviders() {
        return storages.keySet();
    }

    @Override
    public void close() {
        storages.forEach((provider, storage) -> {
            try {
                storage.close();
            } catch (Exception exception) {
                LOGGER.warn("Failed to close storage provider {}", provider.code(), exception);
            }
        });
    }
}