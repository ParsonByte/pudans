package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Getter;


public enum SmsType implements BaseEnum {
    // Bind phone number
    BIND_PHONE("bind_phone", "绑定手机号", MessageCategory.CODE),
    // Phone binding or change successful
    BIND_PHONE_SUCCESS("bind_phone_success", "绑定手机号成功",
            MessageCategory.CODE),
    // Change binding phone number
    UPDATE_PHONE("update_phone", "修改手机号",
            MessageCategory.CODE),
    // Forgot password
    FORGET_PASSWORD("forget_password", "忘记密码",
            MessageCategory.CODE),
    // Change password
    UPDATE_PASSWORD("update_password", "修改密码",
            MessageCategory.CODE),
    // Reset password
    RESET_PASSWORD("reset_password", "重置密码",
            MessageCategory.NOTICE),
    // Password reset successful
    RESET_PASSWORD_SUCCESS("reset_password_success", "重置密码成功",
            MessageCategory.NOTICE),
    // Login verification
    LOGIN("login", "登录验证", MessageCategory.CODE),

    // Welcome SMS
    WELCOME_SMS("welcome_sms", "欢迎短信", MessageCategory.NOTICE),

    // Password expiration reminder
    PASSWORD_SOON_EXPIRED_REMIND("password_soon_expired_remind",
            "密码过期提醒",
            MessageCategory.NOTICE);

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    // SMS type
    @Getter
    private final MessageCategory category;

    SmsType(String code, String desc, MessageCategory category) {
        this.code = code;
        this.desc = desc;
        this.category = category;
    }

    // Get type
    // Parameter code: String
    // Returns: SmsType
    @EnumConvert
    public static SmsType getType(String code) {
        SmsType[] values = values();
        for (SmsType status : values) {
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
