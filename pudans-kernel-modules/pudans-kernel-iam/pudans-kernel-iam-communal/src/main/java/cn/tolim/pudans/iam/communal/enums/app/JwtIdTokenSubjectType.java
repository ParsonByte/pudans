package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum JwtIdTokenSubjectType implements BaseEnum {
    // Username
    USER_ID("user_id"),
    // Application account
    APP_USER("app_user");

    // code
    @JsonValue
    private final String code;

    JwtIdTokenSubjectType(String code) {
        this.code = code;
    }

    // Returns the JwtIdTokenSubjectType for the given code, or null if no match.
    @EnumConvert
    public static JwtIdTokenSubjectType getType(String code) {
        JwtIdTokenSubjectType[] values = values();
        for (JwtIdTokenSubjectType status : values) {
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
        return null;
    }

    @Override
    public String toString() {
        return this.code;
    }
}
