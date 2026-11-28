package cn.tolim.pudans.iam.infra.enums;

import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;

public enum SecretType implements BaseEnum {
    LOGIN("login", "Login secret", "PUDANS_LOGIN_SECRET"),
    ENCRYPT("encrypt", "Encryption secret", "PUDANS_ENCRYPT_SECRET");

    private final String code;
    private final String desc;
    private final String key;

    SecretType(String code, String desc, String key) {
        this.code = code;
        this.desc = desc;
        this.key = key;
    }

    @EnumConvert
    public static SecretType getType(String code) {
        for (SecretType value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    @Override
    @JsonValue
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    public String getKey() {
        return key;
    }

    @Override
    public String toString() {
        return code;
    }
}
