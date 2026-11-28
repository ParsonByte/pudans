package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum AuthorizationType implements BaseEnum {
    // Manual authorization
    AUTHORIZATION("authorization", "手动授权"),
    // Accessible to everyone
    ALL_ACCESS("all_access", "全员可访问");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    AuthorizationType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: AuthorizationType
    @EnumConvert
    public static AuthorizationType getType(String code) {
        AuthorizationType[] values = values();
        for (AuthorizationType status : values) {
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
