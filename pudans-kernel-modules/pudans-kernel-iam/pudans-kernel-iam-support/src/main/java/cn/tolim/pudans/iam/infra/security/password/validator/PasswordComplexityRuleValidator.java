package cn.tolim.pudans.iam.infra.security.password.validator;

import cn.tolim.pudans.iam.infra.security.password.PasswordValidator;
import cn.tolim.pudans.iam.infra.security.password.enums.PasswordComplexityRule;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordComplexityRuleInvalidException;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;

public final class PasswordComplexityRuleValidator implements PasswordValidator {

    private static final String SPECIAL = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~";

    private final PasswordComplexityRule complexityRule;

    public PasswordComplexityRuleValidator(PasswordComplexityRule complexityRule) {
        this.complexityRule = complexityRule == null ? PasswordComplexityRule.NONE
                : complexityRule;
    }

    @Override
    public void validate(String password) throws PasswordInvalidException {
        String value = password == null ? "" : password;
        boolean digit = hasAny(value, "0123456789");
        boolean upper = hasAny(value, "ABCDEFGHIJKLMNOPQRSTUVWXYZ");
        boolean lower = hasAny(value, "abcdefghijklmnopqrstuvwxyz");
        boolean letter = upper || lower;
        boolean special = hasAny(value, SPECIAL);
        switch (complexityRule) {
            case NONE -> {
            }
            case MUST_NUMBERS_AND_LETTERS -> {
                if (!digit || !letter) {
                    throw new PasswordComplexityRuleInvalidException("密码必须包含数字和字母");
                }
            }
            case MUST_NUMBERS_AND_CAPITAL_LETTERS -> {
                if (!digit || !upper) {
                    throw new PasswordComplexityRuleInvalidException("密码必须包含数字和大写字母");
                }
            }
            case MUST_CONTAIN_NUMBERS_UPPERCASE_LETTERS_LOWERCASE_LETTERS_AND_SPECIAL_CHARACTERS -> {
                if (!digit || !upper || !lower || !special) {
                    throw new PasswordComplexityRuleInvalidException(
                            "密码必须包含数字、大写字母、小写字母和特殊字符");
                }
            }
            case CONTAIN_AT_LEAST_TWO_OF_NUMBERS_LETTERS_AND_SPECIAL_CHARACTERS -> {
                int categories = (digit ? 1 : 0) + (letter ? 1 : 0) + (special ? 1 : 0);
                if (categories < 2) {
                    throw new PasswordComplexityRuleInvalidException(
                            "密码至少包含数字、字母和特殊字符中的两种");
                }
            }
            case CONTAIN_AT_LEAST_THREE_OF_NUMBERS_UPPERCASE_LETTERS_LOWERCASE_LETTERS_AND_SPECIAL_CHARACTERS -> {
                int categories = (digit ? 1 : 0) + (upper ? 1 : 0) + (lower ? 1 : 0)
                        + (special ? 1 : 0);
                if (categories < 3) {
                    throw new PasswordComplexityRuleInvalidException(
                            "密码至少包含数字、大写字母、小写字母和特殊字符中的三种");
                }
            }
        }
    }

    private boolean hasAny(String value, String characters) {
        for (int index = 0; index < value.length(); index++) {
            if (characters.indexOf(value.charAt(index)) > -1) {
                return true;
            }
        }
        return false;
    }
}
