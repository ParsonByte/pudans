package cn.tolim.pudans.iam.infra.security.password.enums;

import cn.tolim.pudans.iam.infra.enums.BaseEnum;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PasswordComplexityRule implements BaseEnum {

    NONE("0", "任意密码"),
    MUST_NUMBERS_AND_LETTERS("1", "必须包含数字和字母"),
    MUST_NUMBERS_AND_CAPITAL_LETTERS("2", "必须包含数字和大写字母"),
    MUST_CONTAIN_NUMBERS_UPPERCASE_LETTERS_LOWERCASE_LETTERS_AND_SPECIAL_CHARACTERS("3",
            "必须包含数字、大写字母、小写字母、和特殊字符"),
    CONTAIN_AT_LEAST_TWO_OF_NUMBERS_LETTERS_AND_SPECIAL_CHARACTERS("4",
            "至少包含数字、字母、和特殊字符中的两种"),
    CONTAIN_AT_LEAST_THREE_OF_NUMBERS_UPPERCASE_LETTERS_LOWERCASE_LETTERS_AND_SPECIAL_CHARACTERS(
            5 + "", "至少包含数字、大写字母、小写字母、和特殊字符中的三种");

    private final String code;
    private final String desc;

    PasswordComplexityRule(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static PasswordComplexityRule getType(String code) {
        for (PasswordComplexityRule rule : values()) {
            if (rule.code.equals(code)) {
                return rule;
            }
        }
        throw new IllegalArgumentException("未知的密码复杂度规则：" + code);
    }

    @Override
    @JsonValue
    public String getCode() {
        return code;
    }

    @Override
    public String getDesc() {
        return desc;
    }

    @Override
    public String toString() {
        return code;
    }
}
