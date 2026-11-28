package cn.tolim.pudans.iam.protocol.saml.constant;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import cn.tolim.pudans.iam.infra.web.converter.EnumConvert;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;


public enum SamlNameIdFormat implements BaseEnum {
    // Persistent identifier
    PERSISTENT("urn:oasis:names:tc:SAML:2.0:nameid-format:persistent", "persistent"),

    // Transient identifier
    TRANSIENT("urn:oasis:names:tc:SAML:2.0:nameid-format:transient", "transient"),

    // Email address identifier
    EMAIL("urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress", "email"),

    // Unspecified identifier
    UNSPECIFIED("urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified", "unspecified"),

    // Entity identifier
    ENTITY("urn:oasis:names:tc:SAML:2.0:nameid-format:entity", "entity");

    @JsonValue
    private final String code;

    private final String desc;

    SamlNameIdFormat(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    // Resolve a name id format by its URN or short name.
    @JsonCreator
    @EnumConvert
    public static SamlNameIdFormat getType(String code) {
        for (SamlNameIdFormat value : values()) {
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