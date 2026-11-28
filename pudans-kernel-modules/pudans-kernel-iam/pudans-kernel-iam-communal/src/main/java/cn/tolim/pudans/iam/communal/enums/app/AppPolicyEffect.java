package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum AppPolicyEffect implements BaseEnum {
    // Allow
    Allow("ALLOW", "允许"),
    // Deny
    Deny("DENY", "拒绝");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    AppPolicyEffect(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: AppPolicyEffect
    @EnumConvert
    public static AppPolicyEffect getType(String code) {
        AppPolicyEffect[] values = values();
        for (AppPolicyEffect status : values) {
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
