package cn.tolim.pudans.iam.communal.enums.account;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum UserGender implements BaseEnum {
    // Male
    MALE("1", "男"),
    // Female
    FEMALE("0", "女"),
    // Unknown
    UNKNOWN("-1", "未知");

    @JsonValue
    private final String code;
    private final String desc;

    UserGender(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Get authentication platform
    // Parameter code: String
    // Returns: UserGender
    @EnumConvert
    public static UserGender getType(String code) {
        UserGender[] values = values();
        for (UserGender status : values) {
            if (String.valueOf(status.getCode()).equals(code)) {
                return status;
            }
        }
        throw new NullPointerException("未获取到对应性别");
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
