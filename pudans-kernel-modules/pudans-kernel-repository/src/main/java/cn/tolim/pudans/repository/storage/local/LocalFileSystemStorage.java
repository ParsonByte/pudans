package cn.tolim.pudans.repository.storage.local;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.storage.ObjectStorage;
import cn.tolim.pudans.repository.storage.StorageException;
import cn.tolim.pudans.repository.storage.StorageObjectNotFoundException;
import cn.tolim.pudans.repository.storage.StorageObjectStat;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;

public final class LocalFileSystemStorage implements ObjectStorage {

    private static final int BUFFER_SIZE = 8192;

    private final Path root;

    public LocalFileSystemStorage(RepositoryProperties.Local properties) {
        this.root = Paths.get(properties.getBaseDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException exception) {
            throw new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                    "Local storage directory could not be created: " + root, exception);
        }
    }

    @Override
    public StorageProviderType provider() {
        return StorageProviderType.LOCAL;
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long size, String contentType,
                            Map<String, String> metadata) {
        Path target = resolve(objectKey);
        Path temporary = null;
        try {
            Path parent = target.getParent();
            Files.createDirectories(parent);
            temporary = Files.createTempFile(parent, ".upload-", ".tmp");
            long written = copy(content, temporary);
            if (size >= 0 && written != size) {
                Files.deleteIfExists(temporary);
                temporary = null;
                throw new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                        "Uploaded byte count did not match the declared size");
            }
            move(temporary, target);
            temporary = null;
            return new StoredObject(objectKey, written, null);
        } catch (IOException exception) {
            throw new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                    "Local object write failed: " + objectKey, exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // Temporary file cleanup is best effort
                }
            }
        }
    }

    @Override
    public InputStream open(String objectKey) {
        Path path = resolve(objectKey);
        if (!Files.isRegularFile(path)) {
            throw new StorageObjectNotFoundException(objectKey);
        }
        try {
            return Files.newInputStream(path);
        } catch (IOException exception) {
            throw new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                    "Local object read failed: " + objectKey, exception);
        }
    }

    @Override
    public StorageObjectStat stat(String objectKey) {
        Path path = resolve(objectKey);
        if (!Files.isRegularFile(path)) {
            throw new StorageObjectNotFoundException(objectKey);
        }
        try {
            return new StorageObjectStat(objectKey, Files.size(path), null, null);
        } catch (IOException exception) {
            throw new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                    "Local object stat failed: " + objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        Path path = resolve(objectKey);
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                    "Local object delete failed: " + objectKey, exception);
        }
    }

    @Override
    public URI presignedGetUrl(String objectKey, Duration timeToLive) {
        throw new StorageException(RepositoryErrorCode.UNSUPPORTED_OPERATION,
                "Local storage does not issue presigned URLs");
    }

    @Override
    public boolean directDownload() {
        return false;
    }

    // Resolve a key under the storage root and reject traversal attempts
    Path resolve(String objectKey) {
        if (objectKey == null || objectKey.isBlank() || objectKey.startsWith("/")
                || objectKey.contains("\\") || objectKey.contains("\u0000")) {
            throw new StorageException(RepositoryErrorCode.INVALID_OBJECT_KEY,
                    "Invalid object key");
        }
        Path resolved = root.resolve(objectKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new StorageException(RepositoryErrorCode.INVALID_OBJECT_KEY,
                    "Object key escapes the storage root");
        }
        return resolved;
    }

    private static long copy(InputStream content, Path target) throws IOException {
        long total = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream output = Files.newOutputStream(target)) {
            int read;
            while ((read = content.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                total += read;
            }
        }
        return total;
    }

    private static void move(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Path root() {
        return root;
    }
}