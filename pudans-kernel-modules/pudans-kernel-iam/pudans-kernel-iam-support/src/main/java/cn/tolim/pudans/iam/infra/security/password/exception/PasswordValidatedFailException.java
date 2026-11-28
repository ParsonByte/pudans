package cn.tolim.pudans.iam.infra.security.password.exception;

import org.springframework.http.HttpStatus;

public class PasswordValidatedFailException extends PasswordInvalidException {

    public static final String ERROR_CODE = "password_validated_fail_error";

    public PasswordValidatedFailException() {
        this("密码验证失败");
    }

    public PasswordValidatedFailException(String message) {
        super(ERROR_CODE, message, HttpStatus.BAD_REQUEST);
    }
}
