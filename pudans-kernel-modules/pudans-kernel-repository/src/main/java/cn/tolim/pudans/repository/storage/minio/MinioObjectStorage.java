package cn.tolim.pudans.repository.storage.minio;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.storage.ObjectStorage;
import cn.tolim.pudans.repository.storage.StorageException;
import cn.tolim.pudans.repository.storage.StorageObjectNotFoundException;
import cn.tolim.pudans.repository.storage.StorageObjectStat;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.StoredObject;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.minio.http.Method;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

public final class MinioObjectStorage implements ObjectStorage {

    private static final String PROVIDER = "MinIO";
    private static final long DEFAULT_PART_SIZE = 10L * 1024 * 1024;
    private static final int MAX_PRESIGN_SECONDS = 7 * 24 * 60 * 60;

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(RepositoryProperties.Minio properties, String secretKey) {
        require(properties.getEndpoint(), "endpoint");
        require(properties.getBucket(), "bucket");
        require(properties.getAccessKey(), "accessKey");
        require(secretKey, "secretKey");
        this.bucket = properties.getBucket();
        this.client = MinioClient.builder().endpoint(properties.getEndpoint())
                .credentials(properties.getAccessKey(), secretKey).build();
    }

    @Override
    public StorageProviderType provider() {
        return StorageProviderType.MINIO;
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long size, String contentType,
                            Map<String, String> metadata) {
        if (size < 0) {
            throw new StorageException(RepositoryErrorCode.INVALID_REQUEST,
                    "MinIO uploads require a known content length");
        }
        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder().bucket(bucket).object(objectKey)
                    .stream(content, size, DEFAULT_PART_SIZE);
            if (contentType != null) {
                builder.contentType(contentType);
            }
            if (metadata != null && !metadata.isEmpty()) {
                builder.userMetadata(metadata);
            }
            ObjectWriteResponse response = client.putObject(builder.build());
            return new StoredObject(objectKey, size, response.etag());
        } catch (ErrorResponseException exception) {
            throw translate(objectKey, exception);
        } catch (MinioException | java.io.IOException | java.security.GeneralSecurityException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (ErrorResponseException exception) {
            throw translate(objectKey, exception);
        } catch (MinioException | java.io.IOException | java.security.GeneralSecurityException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public StorageObjectStat stat(String objectKey) {
        try {
            StatObjectResponse stat = client.statObject(StatObjectArgs.builder().bucket(bucket)
                    .object(objectKey).build());
            return new StorageObjectStat(objectKey, stat.size(), stat.contentType(), stat.etag());
        } catch (ErrorResponseException exception) {
            throw translate(objectKey, exception);
        } catch (MinioException | java.io.IOException | java.security.GeneralSecurityException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (ErrorResponseException exception) {
            throw translate(objectKey, exception);
        } catch (MinioException | java.io.IOException | java.security.GeneralSecurityException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public URI presignedGetUrl(String objectKey, Duration timeToLive) {
        try {
            int seconds = (int) Math.min(Math.max(timeToLive.toSeconds(), 1), MAX_PRESIGN_SECONDS);
            String url = client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(objectKey).expiry(seconds).build());
            return URI.create(url);
        } catch (ErrorResponseException exception) {
            throw translate(objectKey, exception);
        } catch (MinioException | java.io.IOException | java.security.GeneralSecurityException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public void close() throws Exception {
        client.close();
    }

    private static StorageException translate(String objectKey, ErrorResponseException exception) {
        String code = exception.errorResponse() == null ? null : exception.errorResponse().code();
        if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
            return new StorageObjectNotFoundException(objectKey);
        }
        return new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                PROVIDER + " operation failed for " + objectKey + ": " + code, exception);
    }

    private static StorageException failure(String objectKey, Exception exception) {
        return new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                PROVIDER + " operation failed for " + objectKey, exception);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new StorageException(RepositoryErrorCode.STORAGE_NOT_CONFIGURED,
                    "MinIO " + name + " is required");
        }
    }
}