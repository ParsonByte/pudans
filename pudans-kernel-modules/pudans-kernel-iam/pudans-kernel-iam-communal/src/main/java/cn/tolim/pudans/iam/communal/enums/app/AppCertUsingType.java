package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum AppCertUsingType implements BaseEnum {
    // OIDC JWK
    OIDC_JWK("oidc_jwk", "OIDC JWK"),

    // JWT encryption
    JWT_ENCRYPT("jwt_encrypt", "JWT 加密");

    @JsonValue
    private final String code;
    // name
    private final String desc;

    AppCertUsingType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumConvert
    public static AppCertUsingType getType(String code) {
        AppCertUsingType[] values = values();
        for (AppCertUsingType type : values) {
            if (String.valueOf(type.getCode()).equals(code)) {
                return type;
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
}
