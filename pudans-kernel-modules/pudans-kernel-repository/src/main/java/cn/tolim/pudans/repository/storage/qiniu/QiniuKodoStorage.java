package cn.tolim.pudans.repository.storage.qiniu;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.error.RepositoryErrorCode;
import cn.tolim.pudans.repository.storage.ObjectStorage;
import cn.tolim.pudans.repository.storage.StorageException;
import cn.tolim.pudans.repository.storage.StorageObjectNotFoundException;
import cn.tolim.pudans.repository.storage.StorageObjectStat;
import cn.tolim.pudans.repository.storage.StorageProviderType;
import cn.tolim.pudans.repository.storage.StoredObject;
import com.qiniu.common.QiniuException;
import com.qiniu.http.Response;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.DownloadUrl;
import com.qiniu.storage.Region;
import com.qiniu.storage.UploadManager;
import com.qiniu.storage.model.DefaultPutRet;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;
import com.qiniu.util.Json;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.time.Duration;
import java.util.Map;

public final class QiniuKodoStorage implements ObjectStorage {

    private static final String PROVIDER = "Qiniu Kodo";
    private static final int NOT_FOUND_CODE = 612;
    private static final int CONNECT_TIMEOUT_MILLIS = 10_000;
    private static final int READ_TIMEOUT_MILLIS = 60_000;

    private final Auth auth;
    private final UploadManager uploadManager;
    private final BucketManager bucketManager;
    private final String bucket;
    private final String domain;
    private final boolean secure;

    public QiniuKodoStorage(RepositoryProperties.Qiniu properties, String secretKey) {
        require(properties.getAccessKey(), "accessKey");
        require(secretKey, "secretKey");
        require(properties.getBucket(), "bucket");
        require(properties.getDomain(), "domain");
        this.bucket = properties.getBucket();
        this.domain = properties.getDomain().trim().replaceFirst("^https?://", "");
        this.secure = properties.getDomain().trim().startsWith("https://");
        this.auth = Auth.create(properties.getAccessKey(), secretKey);
        String regionId = properties.getRegion() == null || properties.getRegion().isBlank()
                ? "z0" : properties.getRegion().trim();
        Configuration configuration = new Configuration(Region.createWithRegionId(regionId));
        this.uploadManager = new UploadManager(configuration);
        this.bucketManager = new BucketManager(auth, configuration);
    }

    @Override
    public StorageProviderType provider() {
        return StorageProviderType.QINIU_KODO;
    }

    @Override
    public StoredObject put(String objectKey, InputStream content, long size, String contentType,
                            Map<String, String> metadata) {
        try {
            String uploadToken = auth.uploadToken(bucket);
            Response response = uploadManager.put(content, objectKey, uploadToken, null, null);
            DefaultPutRet result = Json.decode(response.bodyString(), DefaultPutRet.class);
            return new StoredObject(objectKey, size, result == null ? null : result.hash);
        } catch (QiniuException exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public InputStream open(String objectKey) {
        try {
            URLConnection connection = presignedGetUrl(objectKey, Duration.ofMinutes(15))
                    .toURL().openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
            connection.setReadTimeout(READ_TIMEOUT_MILLIS);
            return connection.getInputStream();
        } catch (IOException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public StorageObjectStat stat(String objectKey) {
        try {
            FileInfo info = bucketManager.stat(bucket, objectKey);
            return new StorageObjectStat(objectKey, info.fsize, info.mimeType, info.hash);
        } catch (QiniuException exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public void delete(String objectKey) {
        try {
            bucketManager.delete(bucket, objectKey);
        } catch (QiniuException exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    @Override
    public URI presignedGetUrl(String objectKey, Duration timeToLive) {
        try {
            DownloadUrl url = new DownloadUrl(domain, secure, objectKey);
            long seconds = Math.max(1, timeToLive == null ? 900 : timeToLive.toSeconds());
            return URI.create(url.buildURL(auth, seconds));
        } catch (QiniuException exception) {
            throw translate(objectKey, exception);
        } catch (RuntimeException exception) {
            throw failure(objectKey, exception);
        }
    }

    private static StorageException translate(String objectKey, QiniuException exception) {
        if (exception.code() == NOT_FOUND_CODE) {
            return new StorageObjectNotFoundException(objectKey);
        }
        return new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                PROVIDER + " operation failed for " + objectKey + ": " + exception.getMessage(),
                exception);
    }

    private static StorageException failure(String objectKey, Exception exception) {
        return new StorageException(RepositoryErrorCode.STORAGE_OPERATION_FAILED,
                PROVIDER + " operation failed for " + objectKey, exception);
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new StorageException(RepositoryErrorCode.STORAGE_NOT_CONFIGURED,
                    "Qiniu " + name + " is required");
        }
    }
}