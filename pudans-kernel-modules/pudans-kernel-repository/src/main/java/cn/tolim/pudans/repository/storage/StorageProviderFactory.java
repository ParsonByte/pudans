package cn.tolim.pudans.repository.storage;

import cn.tolim.pudans.repository.config.RepositoryProperties;
import cn.tolim.pudans.repository.security.SecretCipher;
import cn.tolim.pudans.repository.storage.aliyun.AliyunOssStorage;
import cn.tolim.pudans.repository.storage.local.LocalFileSystemStorage;
import cn.tolim.pudans.repository.storage.minio.MinioObjectStorage;
import cn.tolim.pudans.repository.storage.qiniu.QiniuKodoStorage;
import cn.tolim.pudans.repository.storage.s3.S3ObjectStorage;
import cn.tolim.pudans.repository.storage.tencent.TencentCosStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

public final class StorageProviderFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(StorageProviderFactory.class);

    private StorageProviderFactory() {
    }

    // Build the registry from module properties
    public static ObjectStorageRegistry create(RepositoryProperties properties, SecretCipher cipher) {
        Map<StorageProviderType, ObjectStorage> storages = new EnumMap<>(StorageProviderType.class);
        if (properties.getLocal().isEnabled()) {
            storages.put(StorageProviderType.LOCAL,
                    new LocalFileSystemStorage(properties.getLocal()));
        }
        RepositoryProperties.AliyunOss aliyun = properties.getAliyunOss();
        if (aliyun.isEnabled()) {
            storages.put(StorageProviderType.ALIYUN_OSS, new AliyunOssStorage(aliyun,
                    cipher.decryptIfEncrypted(aliyun.getAccessKeySecret())));
        }
        RepositoryProperties.TencentCos tencent = properties.getTencentCos();
        if (tencent.isEnabled()) {
            storages.put(StorageProviderType.TENCENT_COS, new TencentCosStorage(tencent,
                    cipher.decryptIfEncrypted(tencent.getSecretKey())));
        }
        RepositoryProperties.Minio minio = properties.getMinio();
        if (minio.isEnabled()) {
            storages.put(StorageProviderType.MINIO, new MinioObjectStorage(minio,
                    cipher.decryptIfEncrypted(minio.getSecretKey())));
        }
        RepositoryProperties.Qiniu qiniu = properties.getQiniu();
        if (qiniu.isEnabled()) {
            storages.put(StorageProviderType.QINIU_KODO, new QiniuKodoStorage(qiniu,
                    cipher.decryptIfEncrypted(qiniu.getSecretKey())));
        }
        RepositoryProperties.S3 s3 = properties.getS3();
        if (s3.isEnabled()) {
            storages.put(StorageProviderType.S3, new S3ObjectStorage(s3,
                    cipher.decryptIfEncrypted(s3.getSecretAccessKey())));
        }
        StorageProviderType defaultProvider = properties.getDefaultProvider();
        if (!storages.containsKey(defaultProvider)) {
            throw new StorageNotConfiguredException(defaultProvider);
        }
        LOGGER.info("Configured file storage providers {} with default {}", storages.keySet(),
                defaultProvider.code());
        return ObjectStorageRegistry.of(storages, defaultProvider);
    }
}