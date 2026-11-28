package cn.tolim.pudans.iam.protocol.saml.constant;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;


public enum SamlBindingType implements BaseEnum {
    // HTTP redirect binding
    REDIRECT("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect", "redirect"),

    // HTTP post binding
    POST("urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST", "post");

    @JsonValue
    private final String code;

    private final String desc;

    SamlBindingType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Resolve a binding by its URN or short name.
    @JsonCreator
    @EnumConvert
    public static SamlBindingType getType(String code) {
        for (SamlBindingType value : values()) {
            if (Objects.equals(value.code, code) || Objects.equals(value.desc, code)
                    || Objects.equals(value.name(), code)) {
                return value;
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