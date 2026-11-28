package cn.tolim.pudans.iam.infra.security.password.validator;

import cn.tolim.pudans.iam.infra.security.password.PasswordValidator;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;
import cn.tolim.pudans.iam.infra.security.password.exception.PasswordWeakInvalidException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class WeakPasswordValidator implements PasswordValidator {

    private final Boolean enabled;
    private final Set<String> weakPasswords;

    public WeakPasswordValidator(Boolean enabled) {
        this.enabled = enabled;
        this.weakPasswords = new HashSet<>(16);
    }

    public WeakPasswordValidator(List<String> weakPasswords) {
        this.enabled = true;
        this.weakPasswords = new HashSet<>(weakPasswords);
    }

    public WeakPasswordValidator(Boolean enabled, Set<String> weakPasswords) {
        this.enabled = enabled;
        this.weakPasswords = weakPasswords;
    }

    @Override
    public void validate(String password) throws PasswordInvalidException {
        if (Boolean.TRUE.equals(enabled) && weakPasswords.contains(password)) {
            throw new PasswordWeakInvalidException("密码为弱密码");
        }
    }
}
