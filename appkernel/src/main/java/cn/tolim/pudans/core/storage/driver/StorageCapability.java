package cn.tolim.pudans.core.storage.driver;

public enum StorageCapability {
    PRESIGNED_SOURCE,
    PRESIGNED_PARTS,
    MULTIPART_UPLOAD,
    DIRECT_UPLOAD,
    RANGE_READ,
    PROXY_REQUIRED,
    CORS_CONFIGURABLE
}
