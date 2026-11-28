package cn.tolim.pudans.iam.communal.enums.identitysource;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum IdentitySourceObjectType implements BaseEnum {

    // User
    USER("user", "用户"),
    // Organization
    ORGANIZATION("organization", "组织");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    IdentitySourceObjectType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: IdentitySourceObjectType
    @EnumConvert
    public static IdentitySourceObjectType getType(String code) {
        IdentitySourceObjectType[] values = values();
        for (IdentitySourceObjectType status : values) {
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
