package cn.tolim.pudans.contract.repository;

// Shared constants for file repository service-to-service contracts
public final class RepositoryContractConstants {

    public static final String SERVICE_NAME = "pudans-repository";
    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String DEFAULT_PAGE_NUMBER = "0";
    public static final String DEFAULT_PAGE_SIZE = "20";
    public static final String PROVIDER_LOCAL = "local";
    public static final String PROVIDER_ALIYUN_OSS = "aliyun_oss";
    public static final String PROVIDER_TENCENT_COS = "tencent_cos";
    public static final String PROVIDER_MINIO = "minio";
    public static final String PROVIDER_QINIU_KODO = "qiniu_kodo";
    public static final String PROVIDER_S3 = "s3";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DELETED = "DELETED";

    private RepositoryContractConstants() {
    }
}
