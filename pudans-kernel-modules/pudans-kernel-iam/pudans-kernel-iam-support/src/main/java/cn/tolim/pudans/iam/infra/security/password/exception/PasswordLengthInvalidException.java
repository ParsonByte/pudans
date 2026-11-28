package cn.tolim.pudans.iam.infra.security.password.exception;

import org.springframework.http.HttpStatus;

public class PasswordLengthInvalidException extends PasswordInvalidException {

    public PasswordLengthInvalidException(String message) {
        super(message);
    }

    public PasswordLengthInvalidException(String message, HttpStatus status) {
        super(message, status);
    }

    public PasswordLengthInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public PasswordLengthInvalidException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public PasswordLengthInvalidException(Throwable cause, String errorCode, String message,
                                          HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
