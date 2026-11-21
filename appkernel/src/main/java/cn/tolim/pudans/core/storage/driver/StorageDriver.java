package cn.tolim.pudans.core.storage.driver;

import cn.tolim.pudans.contract.storage.StoragePolicyType;
import java.util.Collection;
import java.util.Set;
import reactor.core.publisher.Mono;

public interface StorageDriver extends AutoCloseable {

    StoragePolicyType type();

    Set<StorageCapability> capabilities();

    Mono<StorageObject> put(StoragePutRequest request);

    Mono<StorageReadResult> get(String key, StorageReadOptions options);

    Mono<StorageObject> stat(String key);

    Mono<Void> delete(Collection<String> keys);

    Mono<StorageSource> source(String key, StorageSourceOptions options);

    Mono<StorageTestResult> testConnection();

    @Override
    default void close() {}
}
