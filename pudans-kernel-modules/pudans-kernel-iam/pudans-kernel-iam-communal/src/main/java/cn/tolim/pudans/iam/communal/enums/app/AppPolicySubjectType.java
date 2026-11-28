package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum AppPolicySubjectType implements BaseEnum {
    // Role
    ROLE("ROLE", "角色"),
    // User
    USER("USER", "用户"),
    // Organization
    ORGANIZATION("ORGANIZATION", "组织机构"),
    // Group
    USER_GROUP("USER_GROUP", "分组"),
    // Client
    CLIENT("CLIENT", "客户端");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    AppPolicySubjectType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: AppPolicySubjectType
    @EnumConvert
    public static AppPolicySubjectType getType(String code) {
        AppPolicySubjectType[] values = values();
        for (AppPolicySubjectType status : values) {
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
