package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum MessageNoticeChannel implements BaseEnum {
    // Email
    MAIL("mail", "邮件"),
    // SMS
    SMS("sms", "短信");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    MessageNoticeChannel(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: MessageNoticeChannel
    @EnumConvert
    public static MessageNoticeChannel getType(String code) {
        MessageNoticeChannel[] values = values();
        for (MessageNoticeChannel status : values) {
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
