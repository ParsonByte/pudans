package cn.tolim.pudans.iam.infra.security.password;

import cn.tolim.pudans.iam.infra.security.password.exception.PasswordInvalidException;

@FunctionalInterface
public interface PasswordValidator {

    void validate(String password) throws PasswordInvalidException;
}
