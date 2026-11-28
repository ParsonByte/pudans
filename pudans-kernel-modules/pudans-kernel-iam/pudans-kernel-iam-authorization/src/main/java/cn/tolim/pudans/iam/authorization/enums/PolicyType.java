package cn.tolim.pudans.iam.authorization.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum PolicyType implements BaseEnum {
    // Built-in system policy
    SYSTEM("SYSTEM", "System"),

    // User defined custom policy
    CUSTOM("CUSTOM", "Custom");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    PolicyType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get policy type by code
    // Parameter code: String
    // Returns: PolicyType
    @EnumConvert
    public static PolicyType getType(String code) {
        PolicyType[] values = values();
        for (PolicyType type : values) {
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

    @Override
    public String toString() {
        return code;
    }
}