package cn.tolim.pudans.repository.storage.aliyun;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.storage.ObjectStorage;
import cn.tolim.pudans.repository.storage.StorageException;
import cn.tolim.pudans.repository.storage.StorageObjectNotFoundException;
import cn.tolim.pudans.repository.storage.StorageObjectStat;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.StoredObject;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.PutObjectResult;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public final class AliyunOssStorage implements ObjectStorage {

    private static final String PROVIDER = "Aliyun OSS";

    private final OSS client;
    private final String bucket;

    public AliyunOssStorage(RepositoryProperties.AliyunOss properties, String accessKeySecret) {
        require(properties.getEndpoint(), "endpoint");
        require(properties.getBucket(), "bucket");
        require(properties.getAccessKeyId(), "accessKeyId");
        require(accessKeySecret, "accessKeySecret");
        this.bucket = properties.getBucket();
        this.client = new OSSClientBuilder().build(properties.getEndpoint(),
                properties.getAccessKeyId(), accessKeySecret);
    }

    @Override
    public StorageProviderType provider() {
        return StorageProviderType.ALIYUN_OSS;
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long size, String contentType,
                            Map<String, String> metadata) {
        try {
            ObjectMetadata objectMetadata = new ObjectMetadata();
            if (size >= 0) {
                objectMetadata.setContentLength(size);
            }
            if (contentType != null) {
                objectMetadata.setContentType(contentType);
            }
            if (metadata != null && !metadata.isEmpty()) {
                objectMetadata.setUserMetadata(metadata);
            }
            PutObjectResult result = client.putObject(bucket, objectKey, content, objectMetadata);
            return new StoredObject(objectKey, size, result.getETag());
        } catch (OSSException exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return client.getObject(bucket, objectKey).getObjectContent();
        } catch (OSSException exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public StorageObjectStat stat(String objectKey) {
        try {
            ObjectMetadata metadata = client.getObjectMetadata(bucket, objectKey);
            return new StorageObjectStat(objectKey, metadata.getContentLength(),
                    metadata.getContentType(), metadata.getETag());
        } catch (OSSException exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(bucket, objectKey);
        } catch (OSSException exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public URI presignedGetUrl(String objectKey, Duration timeToLive) {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey,
                    HttpMethod.GET);
            request.setExpiration(Date.from(Instant.now().plus(timeToLive)));
            return URI.create(client.generatePresignedUrl(request).toString());
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public void close() {
        client.shutdown();
    }

    private static StorageException translate(String objectKey, OSSException exception) {
        String code = exception.getErrorCode();
        if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
            return new StorageObjectNotFoundException(objectKey);
        }
        return new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                PROVIDER + " operation failed for " + objectKey + ": " + code, exception);
    }

    private static StorageException failure(String objectKey, RuntimeException exception) {
        return new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                PROVIDER + " operation failed for " + objectKey, exception);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new StorageException(RepositoryErrorCode.STORAGE_NOT_CONFIGURED,
                    "Aliyun OSS " + name + " is required");
        }
    }
}