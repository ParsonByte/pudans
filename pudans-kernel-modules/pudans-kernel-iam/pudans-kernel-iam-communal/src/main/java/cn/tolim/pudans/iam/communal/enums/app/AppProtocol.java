package cn.tolim.pudans.iam.communal.enums.app;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonValue;


public enum AppProtocol implements BaseEnum {
    // OIDC
    OIDC("OIDC", "OIDC"),

    // JWT
    JWT("JWT", "JWT"),

    // FORM
    FORM("FORM", "表单代填");

    @JsonValue
    private final String code;
    // name
    private final String desc;

    AppProtocol(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @EnumConvert
    public static AppProtocol getType(String code) {
        AppProtocol[] values = values();
        for (AppProtocol source : values) {
            if (String.valueOf(source.getCode()).equals(code)) {
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
}
