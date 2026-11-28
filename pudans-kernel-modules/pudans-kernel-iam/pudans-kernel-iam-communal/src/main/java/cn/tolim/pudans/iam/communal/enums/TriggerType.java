package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum TriggerType implements BaseEnum {
    // Manual trigger
    MANUAL("manual", "手动触发"),
    // Task trigger
    JOB("job", "任务触发");

    @JsonValue
    private final String code;
    private final String desc;

    TriggerType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: TriggerType
    @EnumConvert
    public static TriggerType getType(String code) {
        TriggerType[] values = values();
        for (TriggerType status : values) {
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
