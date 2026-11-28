package cn.tolim.pudans.iam.communal.storage;

import java.io.InputStream;

public interface Storage {
    // Upload file
    // Parameter fileName: String
    // Parameter inputStream: InputStream
    // Returns: path
    // Throws Exception: Exception
    String upload(String fileName, InputStream inputStream) throws Exception;

    // Download file
    // Parameter path: String
    // Returns: path String
    // Throws Exception: Exception
    String download(String path) throws Exception;

    // Open stored content for streaming through this service
    // Parameter path: String
    // Returns: StorageContent
    // Throws Exception: Exception
    default StorageContent open(String path) throws Exception {
        throw new UnsupportedOperationException("Storage content streaming is not supported");
    }
}