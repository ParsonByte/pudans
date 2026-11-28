package cn.tolim.pudans.iam.authorization.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum AuthorizationStatus implements BaseEnum {
    // Enabled
    ENABLED("ENABLED", "Enabled"),

    // Disabled
    DISABLED("DISABLED", "Disabled");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    AuthorizationStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get authorization status by code
    // Parameter code: String
    // Returns: AuthorizationStatus
    @EnumConvert
    public static AuthorizationStatus getStatus(String code) {
        AuthorizationStatus[] values = values();
        for (AuthorizationStatus status : values) {
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
        return code;
    }
}