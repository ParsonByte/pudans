package cn.tolim.pudans.iam.communal.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum Language implements BaseEnum {
    // English
    EN("en", "英语"),
    // Chinese
    ZH("zh", "中文");

    // code
    @JsonValue
    private final String locale;
    // desc
    private final String desc;

    Language(String locale, String desc) {
        this.locale = locale;
        this.desc = desc;
    }

    // Get type
    // Parameter code: String
    // Returns: Language
    @EnumConvert
    public static Language getType(String code) {
        Language[] values = values();
        for (Language status : values) {
            if (String.valueOf(status.getLocale()).equals(code)) {
                return status;
            }
        }
        return null;
    }

    public String getLocale() {
        return locale;
    }

    @Override
    public String getCode() {
        return locale;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        return this.getLocale();
    }

}
