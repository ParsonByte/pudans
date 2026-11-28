package cn.tolim.pudans.iam.identitysource.feishu.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import com.fasterxml.jackson.annotation.JsonValue;

// Feishu event type
public enum FeiShuEventType implements BaseEnum {
    // Test URL
    URL_VERIFICATION("url_verification", "测试url"),
    // User change: contact added
    USER_ADD_ORG("contact.user.created_v3", "通讯录用户增加"),
    // Contact user changed
    USER_MODIFY_ORG("contact.user.updated_v3",
            "通讯录用户更改"),
    // Contact user departed
    USER_LEAVE_ORG("contact.user.deleted_v3",
            "通讯录用户离职"),
    // Department change: contact enterprise department created
    ORG_DEPT_CREATE("contact.department.created_v3",
            "通讯录企业部门创建"),
    // Contact enterprise department modified
    ORG_DEPT_MODIFY("contact.department.updated_v3",
            "通讯录企业部门修改"),
    // Contact enterprise department deleted
    ORG_DEPT_REMOVE("contact.department.deleted_v3",
            "通讯录企业部门删除");

    // code
    @JsonValue
    private final String code;
    // Name
    private final String name;

    FeiShuEventType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static FeiShuEventType getType(String code) {
        FeiShuEventType[] values = values();
        for (FeiShuEventType status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        throw new NullPointerException("未找到该类型");
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return null;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return this.code;
    }
}
