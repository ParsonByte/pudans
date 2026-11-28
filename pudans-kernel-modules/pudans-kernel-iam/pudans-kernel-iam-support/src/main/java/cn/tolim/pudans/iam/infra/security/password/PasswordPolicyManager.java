package cn.tolim.pudans.iam.infra.security.password;

import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;

public interface PasswordPolicyManager {

    void validate(Object principal, String password) throws PasswordInvalidException;

    void validate(String userId, String password) throws PasswordInvalidException;
}
