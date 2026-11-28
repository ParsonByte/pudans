package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum FormSubmitType implements BaseEnum {
    // POST
    POST("post", "POST"),
    // GET
    GET("get", "GET");

    // code
    @JsonValue
    private final String code;
    // desc
    private final String desc;

    FormSubmitType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Returns the FormSubmitType for the given code, or null if no match.
    @EnumConvert
    public static FormSubmitType getType(String code) {
        FormSubmitType[] values = values();
        for (FormSubmitType status : values) {
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
