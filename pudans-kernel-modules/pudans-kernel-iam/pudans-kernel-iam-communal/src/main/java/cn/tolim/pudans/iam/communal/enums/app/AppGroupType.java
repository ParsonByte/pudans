package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum AppGroupType implements BaseEnum {
    // Default group
    DEFAULT("default", "默认分组"),
    // Custom group
    CUSTOM("custom", "自定义分组");

    @JsonValue
    private final String code;
    private final String desc;

    AppGroupType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: AppGroupType
    @EnumConvert
    public static AppGroupType getType(String code) {
        AppGroupType[] values = values();
        for (AppGroupType status : values) {
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
