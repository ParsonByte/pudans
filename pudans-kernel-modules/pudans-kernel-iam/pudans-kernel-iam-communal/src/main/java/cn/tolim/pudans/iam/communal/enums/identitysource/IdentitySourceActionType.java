package cn.tolim.pudans.iam.communal.enums.identitysource;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum IdentitySourceActionType implements BaseEnum {

    // Create
    INSERT("insert", "新增"),
    // Modify
    UPDATE("update", "修改"),
    // Delete
    DELETE("delete", "删除");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    IdentitySourceActionType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: IdentitySourceActionType
    @EnumConvert
    public static IdentitySourceActionType getType(String code) {
        IdentitySourceActionType[] values = values();
        for (IdentitySourceActionType status : values) {
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
