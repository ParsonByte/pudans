package cn.tolim.pudans.contract.storage.resource;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum StoragePolicyType {
    LOCAL("local"),
    S3("s3"),
    KS3("ks3"),
    OSS("oss"),
    COS("cos"),
    OBS("obs"),
    QINIU("qiniu"),
    UPYUN("upyun"),
    ONEDRIVE("onedrive");

    private final String code;

    StoragePolicyType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static StoragePolicyType fromCode(String code) {
        for (var type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的存储策略类型：" + code);
    }
}
