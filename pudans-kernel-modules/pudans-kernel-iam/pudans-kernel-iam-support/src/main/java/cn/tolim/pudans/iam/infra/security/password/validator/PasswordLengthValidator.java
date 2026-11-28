package cn.tolim.pudans.iam.infra.security.password.validator;

import cn.tolim.pudans.iam.infra.security.password.PasswordValidator;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordLengthInvalidException;

public final class PasswordLengthValidator implements PasswordValidator {

    private final Integer minLength;
    private final Integer maxLength;

    public PasswordLengthValidator(Integer minLength, Integer maxLength) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public void validate(String password) throws PasswordInvalidException {
        String value = password == null ? "" : password;
        int length = value.codePointCount(0, value.length());
        if (minLength != null && minLength > 0 && length < minLength) {
            throw new PasswordLengthInvalidException("密码长度不符合要求");
        }
        if (maxLength != null && maxLength > 0 && length > maxLength) {
            throw new PasswordLengthInvalidException("密码长度不符合要求");
        }
    }
}
