package cn.tolim.pudans.iam.communal.message.enums;

import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;

import java.io.Serializable;


public enum MessageType implements Serializable {
    // Email
    MAIL("mail", "邮件"),
    // SMS
    SMS("sms", "短信");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    MessageType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Returns the MessageType for the given code, or null if no match.
    @EnumConvert
    public static MessageType getType(String code) {
        MessageType[] values = values();
        for (MessageType status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        return null;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        return this.code;
    }

}
