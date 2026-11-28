package cn.tolim.pudans.iam.authorization.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum RiskLevel implements BaseEnum {
    // Low risk
    LOW("LOW", "Low"),

    // Medium risk
    MEDIUM("MEDIUM", "Medium"),

    // High risk
    HIGH("HIGH", "High"),

    // Critical risk, requires full audit context
    CRITICAL("CRITICAL", "Critical");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    RiskLevel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get risk level by code
    // Parameter code: String
    // Returns: RiskLevel
    @EnumConvert
    public static RiskLevel getLevel(String code) {
        RiskLevel[] values = values();
        for (RiskLevel level : values) {
            if (String.valueOf(level.getCode()).equals(code)) {
                return level;
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
        return code;
    }
}