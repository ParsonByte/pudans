package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum AppPolicyObjectType implements BaseEnum {
    // Role
    ROLE("ROLE", "角色"),
    // Permission
    PERMISSION("PERMISSION", "权限"),
    // Resource
    RESOURCE("RESOURCE", "资源");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    AppPolicyObjectType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: AppPolicyObjectType
    @EnumConvert
    public static AppPolicyObjectType getType(String code) {
        AppPolicyObjectType[] values = values();
        for (AppPolicyObjectType status : values) {
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
}
