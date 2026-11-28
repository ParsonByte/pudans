package cn.tolim.pudans.iam.communal.enums.account;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonValue;


public enum UserIdType implements BaseEnum {
    // ID card number
    IDENTITY_CARD("identity_card", "身份证号");

    // code
    @JsonValue
    @JSONField
    private final String code;
    // desc
    private final String desc;

    UserIdType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get source
    // Parameter code: String
    // Returns: UserIdType
    @EnumConvert
    public static UserIdType getType(String code) {
        UserIdType[] values = values();
        for (UserIdType source : values) {
            if (String.valueOf(source.getCode()).equals(code)) {
                return source;
            }
        }
        return null;
    }

    public static UserIdType getName(String name) {
        UserIdType[] values = values();
        for (UserIdType source : values) {
            if (source.name().equals(name)) {
                return source;
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
