package cn.tolim.pudans.repository.storage;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Arrays;
import java.util.Locale;

public enum StorageProviderType {

    LOCAL("local"),
    ALIYUN_OSS("aliyun_oss"),
    TENCENT_COS("tencent_cos"),
    MINIO("minio"),
    QINIU_KODO("qiniu_kodo"),
    S3("s3");

    private final String code;

    StorageProviderType(String code) {
        this.code = code;
    }

    // Resolve a provider from its external code
    public static StorageProviderType fromCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Storage provider code is required");
        }
        String normalized = code.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(value -> value.code.equals(normalized) || value.name().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown storage provider: " + code));
    }

    @JsonValue
    public String code() {
        return code;
    }
}