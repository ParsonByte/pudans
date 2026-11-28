package cn.tolim.pudans.repository.storage.tencent;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.storage.ObjectStorage;
import cn.tolim.pudans.repository.storage.StorageException;
import cn.tolim.pudans.repository.storage.StorageObjectNotFoundException;
import cn.tolim.pudans.repository.storage.StorageObjectStat;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.StoredObject;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.exception.CosClientException;
import com.qcloud.cos.exception.CosServiceException;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.model.GeneratePresignedUrlRequest;
import com.qcloud.cos.model.ObjectMetadata;
import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.region.Region;

import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;

public final class TencentCosStorage implements ObjectStorage {

    private static final String PROVIDER = "Tencent COS";

    private final COSClient client;
    private final String bucket;

    public TencentCosStorage(RepositoryProperties.TencentCos properties, String secretKey) {
        require(properties.getRegion(), "region");
        require(properties.getBucket(), "bucket");
        require(properties.getSecretId(), "secretId");
        require(secretKey, "secretKey");
        this.bucket = properties.getBucket();
        COSCredentials credentials = new BasicCOSCredentials(properties.getSecretId(), secretKey);
        ClientConfig clientConfig = new ClientConfig(new Region(properties.getRegion()));
        clientConfig.setHttpProtocol(HttpProtocol.https);
        this.client = new COSClient(credentials, clientConfig);
    }

    @Override
    public StorageProviderType provider() {
        return StorageProviderType.TENCENT_COS;
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
        } catch (CosServiceException exception) {
            throw translate(objectKey, exception);
        } catch (CosClientException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            return client.getObject(bucket, objectKey).getObjectContent();
        } catch (CosServiceException exception) {
            throw translate(objectKey, exception);
        } catch (CosClientException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public StorageObjectStat stat(String objectKey) {
        try {
            ObjectMetadata metadata = client.getObjectMetadata(bucket, objectKey);
            return new StorageObjectStat(objectKey, metadata.getContentLength(),
                    metadata.getContentType(), metadata.getETag());
        } catch (CosServiceException exception) {
            throw translate(objectKey, exception);
        } catch (CosClientException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            client.deleteObject(bucket, objectKey);
        } catch (CosServiceException exception) {
            throw translate(objectKey, exception);
        } catch (CosClientException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public URI presignedGetUrl(String objectKey, Duration timeToLive) {
        try {
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey,
                    HttpMethodName.GET);
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

    private static StorageException translate(String objectKey, CosServiceException exception) {
        String code = exception.getErrorCode();
        if (exception.getStatusCode() == 404 || "NoSuchKey".equals(code)
                || "NoSuchResource".equals(code)) {
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
                    "Tencent COS " + name + " is required");
        }
    }
}