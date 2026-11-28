package cn.tolim.pudans.iam.infra.security.password.exception;

import org.springframework.http.HttpStatus;

public class PasswordContinuousSameCharInvalidException extends PasswordInvalidException {

    public PasswordContinuousSameCharInvalidException(String message) {
        super(message);
    }

    public PasswordContinuousSameCharInvalidException(String message, HttpStatus status) {
        super(message, status);
    }

    public PasswordContinuousSameCharInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public PasswordContinuousSameCharInvalidException(String errorCode, String message,
                                                      HttpStatus status) {
        super(errorCode, message, status);
    }

    public PasswordContinuousSameCharInvalidException(Throwable cause, String errorCode,
                                                      String message, HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
