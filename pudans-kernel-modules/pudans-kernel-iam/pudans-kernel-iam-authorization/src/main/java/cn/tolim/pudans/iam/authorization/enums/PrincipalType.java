package cn.tolim.pudans.iam.authorization.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum PrincipalType implements BaseEnum {
    // User principal
    USER("USER", "User"),

    // User group principal
    USER_GROUP("USER_GROUP", "User group"),

    // Role principal
    ROLE("ROLE", "Role"),

    // Organization principal
    ORGANIZATION("ORGANIZATION", "Organization"),

    // Client or service principal
    CLIENT("CLIENT", "Client");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    PrincipalType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get principal type by code
    // Parameter code: String
    // Returns: PrincipalType
    @EnumConvert
    public static PrincipalType getType(String code) {
        PrincipalType[] values = values();
        for (PrincipalType type : values) {
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