package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum FormEncryptType implements BaseEnum {
    // AES
    AES("aes", "AES加密"),
    // MD5 encoding
    MD5("md5", "MD5编码"),
    // Base64
    BASE64("base64", "BASE64编码");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    FormEncryptType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Returns the FormEncryptType for the given code, or null if no match.
    @EnumConvert
    public static FormEncryptType getType(String code) {
        if (code == null) {
            return null;
        }
        FormEncryptType[] values = values();
        for (FormEncryptType status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        return null;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        return this.code;
    }
}
