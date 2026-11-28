package cn.tolim.pudans.repository.storage;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

public interface ObjectStorage extends AutoCloseable {

    StorageProviderType provider();

    StoredObject put(String objectKey, InputStream content, long size, String contentType,
                     Map<String, String> metadata);

    InputStream open(String objectKey);

    StorageObjectStat stat(String objectKey);

    void delete(String objectKey);

    // Default existence check derived from the object stat call
    default boolean exists(String objectKey) {
        try {
            stat(objectKey);
            return true;
        } catch (StorageObjectNotFoundException exception) {
            return false;
        }
    }

    URI presignedGetUrl(String objectKey, Duration timeToLive);

    // Whether presigned URLs can be handed directly to clients
    default boolean directDownload() {
        return true;
    }

    @Override
    default void close() throws Exception {
    }
}