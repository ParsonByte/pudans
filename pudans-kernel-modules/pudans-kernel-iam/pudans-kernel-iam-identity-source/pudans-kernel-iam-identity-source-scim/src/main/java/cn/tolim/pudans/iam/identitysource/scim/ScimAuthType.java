package cn.tolim.pudans.iam.identitysource.scim;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;


public enum ScimAuthType {
    // Bearer token authorization
    BEARER("bearer"),
    // HTTP basic authorization
    BASIC("basic"),
    // No authorization header
    NONE("none");

    private final String code;

    ScimAuthType(String code) {
        this.code = code;
    }

    @JsonValue
    public String getCode() {
        return code;
    }

    @JsonCreator
    public static ScimAuthType fromCode(String code) {
        for (ScimAuthType value : values()) {
            if (value.code.equalsIgnoreCase(code)) {
                return value;
            }
        }
        return BEARER;
    }
}