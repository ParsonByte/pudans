package cn.tolim.pudans.iam.authorization.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum PolicyDecision implements BaseEnum {
    // Request is allowed
    ALLOW("ALLOW", "Allow"),

    // Request is explicitly or implicitly denied
    DENY("DENY", "Deny");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    PolicyDecision(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get policy decision by code
    // Parameter code: String
    // Returns: PolicyDecision
    @EnumConvert
    public static PolicyDecision getDecision(String code) {
        PolicyDecision[] values = values();
        for (PolicyDecision decision : values) {
            if (String.valueOf(decision.getCode()).equals(code)) {
                return decision;
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