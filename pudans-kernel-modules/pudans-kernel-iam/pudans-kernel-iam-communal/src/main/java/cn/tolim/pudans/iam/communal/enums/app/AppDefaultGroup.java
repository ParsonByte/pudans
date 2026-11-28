package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;


public enum AppDefaultGroup implements BaseEnum {
    // Development
    DEVELOPMENT("development", "开发类"),

    // Operations
    OPS("ops", "运维类"),

    // Office
    OFFICE("office", "办公类");

    private final String code;
    private final String desc;

    AppDefaultGroup(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Returns the AppDefaultGroup for the given code, or null if no match.
    @EnumConvert
    public static AppDefaultGroup getType(String code) {
        AppDefaultGroup[] values = values();
        for (AppDefaultGroup status : values) {
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
