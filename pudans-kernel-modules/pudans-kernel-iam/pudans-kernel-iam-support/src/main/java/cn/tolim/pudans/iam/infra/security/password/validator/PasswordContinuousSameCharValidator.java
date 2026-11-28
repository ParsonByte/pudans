package cn.tolim.pudans.iam.infra.security.password.validator;

import cn.tolim.pudans.iam.infra.security.password.PasswordValidator;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordContinuousSameCharInvalidException;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;

public final class PasswordContinuousSameCharValidator implements PasswordValidator {

    private final Integer sequenceLength;

    public PasswordContinuousSameCharValidator(Integer sequenceLength) {
        this.sequenceLength = sequenceLength;
    }

    @Override
    public void validate(String password) throws PasswordInvalidException {
        if (sequenceLength == null || sequenceLength < 2) {
            return;
        }
        String value = password == null ? "" : password;
        int repeat = 1;
        for (int index = 1; index < value.length(); index++) {
            if (value.charAt(index) == value.charAt(index - 1)) {
                repeat++;
                if (repeat >= sequenceLength) {
                    throw new PasswordContinuousSameCharInvalidException("密码包含连续相同字符");
                }
            } else {
                repeat = 1;
            }
        }
    }
}
