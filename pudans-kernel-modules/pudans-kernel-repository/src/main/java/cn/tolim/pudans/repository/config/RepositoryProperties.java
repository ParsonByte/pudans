package cn.tolim.pudans.repository.config;

import cn.tolim.pudans.repository.storage.StorageProviderType;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@ConfigurationProperties(prefix = "pudans.repository")
public class RepositoryProperties {

    // Whether the repository module is enabled
    private boolean enabled = true;
    // Provider used for new uploads when a request does not select one
    private StorageProviderType defaultProvider = StorageProviderType.LOCAL;
    // Tenant used when a request does not carry a tenant identifier
    private String defaultTenantId = "default";
    // Identity recorded for uploads when a request does not carry one
    private String defaultCreatedBy = "system";
    // Maximum accepted upload size in bytes
    private long maxFileSize = 100L * 1024 * 1024;
    // Allowed content types; empty allows every type, "image/*" style wildcards are supported
    private Set<String> allowedContentTypes = new LinkedHashSet<>();
    // Lifetime of generated presigned download URLs
    private Duration presignedUrlTtl = Duration.ofMinutes(15);
    // Maximum number of entries returned by a single list request
    private int maxPageSize = 200;

    private final Local local = new Local();
    private final AliyunOss aliyunOss = new AliyunOss();
    private final TencentCos tencentCos = new TencentCos();
    private final Minio minio = new Minio();
    private final Qiniu qiniu = new Qiniu();
    private final S3 s3 = new S3();
    private final Checksum checksum = new Checksum();
    private final Crypto crypto = new Crypto();

    @Getter
    @Setter
    public static class Local {

        // Whether local filesystem storage is available
        private boolean enabled = true;
        // Root directory for stored objects
        private String baseDirectory = "./data/repository";
    }

    @Getter
    @Setter
    public abstract static class CloudStorage {

        // Whether this provider is available
        private boolean enabled;
        // Service endpoint
        private String endpoint;
        // Bucket name
        private String bucket;
        // Public access domain used for generated URLs
        private String domain;
        // Key prefix inside the bucket
        private String location = "pudans";
    }

    @Getter
    @Setter
    public static class AliyunOss extends CloudStorage {

        // Alibaba Cloud access key id
        private String accessKeyId;
        // Alibaba Cloud access key secret, plain or spissatus prefixed
        private String accessKeySecret;
    }

    @Getter
    @Setter
    public static class TencentCos extends CloudStorage {

        // Tencent Cloud app id
        private String appId;
        // Tencent Cloud secret id
        private String secretId;
        // Tencent Cloud secret key, plain or spissatus prefixed
        private String secretKey;
        // Tencent Cloud region
        private String region;
    }

    @Getter
    @Setter
    public static class Minio extends CloudStorage {

        // MinIO access key
        private String accessKey;
        // MinIO secret key, plain or spissatus prefixed
        private String secretKey;
    }

    @Getter
    @Setter
    public static class Qiniu extends CloudStorage {

        // Qiniu access key
        private String accessKey;
        // Qiniu secret key, plain or spissatus prefixed
        private String secretKey;
        // Qiniu storage region id
        private String region = "z0";
    }

    @Getter
    @Setter
    public static class S3 extends CloudStorage {

        // AWS access key id
        private String accessKeyId;
        // AWS secret access key, plain or spissatus prefixed
        private String secretAccessKey;
        // AWS region
        private String region = "us-east-1";
    }

    @Getter
    @Setter
    public static class Checksum {

        // Whether SHA-256 checksums are computed for new uploads
        private boolean enabled = true;
        // Maximum content size that is buffered for checksum computation
        private long maxBytes = 8L * 1024 * 1024;
    }

    @Getter
    @Setter
    public static class Crypto {

        // Key identifier used for encrypting storage secrets
        private String keyId = "pudans-repository-secret";
        // Tenant that owns the encryption key
        private String tenantId = "pudans";
        // Caller identity presented to the crypto facade
        private String callerId = "pudans-repository";
        // Caller environment presented to the crypto facade
        private String callerEnvironment = "default";
        // AES key size in bits
        private int keyBits = 256;
    }
}