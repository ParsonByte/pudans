package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum UserStatus implements BaseEnum {
    // Enabled
    ENABLED("enabled", "启用"),

    // Disabled
    DISABLED("disabled", "禁用"),

    // Locked after login failures
    LOCKED("locked", "锁定"),

    // Expiration lock
    EXPIRED_LOCKED("expired_locked", "过期锁定"),

    // Password expiration lock
    PASSWORD_EXPIRED_LOCKED("password_expired_locked",
            "密码过期锁定");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    UserStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumConvert
    public static UserStatus getStatus(String code) {
        UserStatus[] values = values();
        for (UserStatus status : values) {
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
