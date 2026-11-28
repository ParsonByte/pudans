package cn.tolim.pudans.iam.identitysource.dingtalk.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import com.fasterxml.jackson.annotation.JsonValue;

// DingTalk event type
public enum DingTalkEventType implements BaseEnum {
    // Test
    CHECK_URL("check_url", "测试url"),
    // User change: contact added
    USER_ADD_ORG("user_add_org", "通讯录用户增加"),
    // Contact user changed
    USER_MODIFY_ORG("user_modify_org", "通讯录用户更改"),
    // Contact user departed
    USER_LEAVE_ORG("user_leave_org", "通讯录用户离职"),
    // User activated after joining the enterprise
    USER_ACTIVE_ORG("user_active_org", "加入企业后用户激活"),
    // Department change: contact enterprise department created
    ORG_DEPT_CREATE("org_dept_create", "通讯录企业部门创建"),
    // Contact enterprise department modified
    ORG_DEPT_MODIFY("org_dept_modify", "通讯录企业部门修改"),
    // Contact enterprise department deleted
    ORG_DEPT_REMOVE("org_dept_remove", "通讯录企业部门删除");

    // code
    @JsonValue
    private final String code;
    // Name
    private final String name;

    DingTalkEventType(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public static DingTalkEventType getType(String code) {
        DingTalkEventType[] values = values();
        for (DingTalkEventType status : values) {
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
