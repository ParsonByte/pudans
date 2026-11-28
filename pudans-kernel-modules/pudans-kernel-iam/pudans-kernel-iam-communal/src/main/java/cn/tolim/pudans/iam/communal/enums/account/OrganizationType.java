package cn.tolim.pudans.iam.communal.enums.account;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum OrganizationType implements BaseEnum {
    // Group
    GROUP("group", "集团"),
    // Company
    COMPANY("company", "公司"),
    // Department
    DEPARTMENT("department", "部门"),
    // Unit
    UNIT("unit", "单位");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    OrganizationType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: OrganizationType
    @EnumConvert
    public static OrganizationType getType(String code) {
        OrganizationType[] values = values();
        for (OrganizationType status : values) {
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
