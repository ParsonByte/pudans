package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum MessageCategory implements BaseEnum {
    // Captcha
    CODE("code", "验证码"),
    // Notification
    NOTICE("notice", "通知");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    MessageCategory(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: MessageCategory
    @EnumConvert
    public static MessageCategory getType(String code) {
        MessageCategory[] values = values();
        for (MessageCategory status : values) {
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
