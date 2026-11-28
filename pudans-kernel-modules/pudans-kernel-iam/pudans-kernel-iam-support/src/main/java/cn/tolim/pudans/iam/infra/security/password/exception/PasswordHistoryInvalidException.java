package cn.tolim.pudans.iam.infra.security.password.exception;

import org.springframework.http.HttpStatus;

public class PasswordHistoryInvalidException extends PasswordInvalidException {

    public PasswordHistoryInvalidException(String message) {
        super(message);
    }

    public PasswordHistoryInvalidException(String message, HttpStatus status) {
        super(message, status);
    }

    public PasswordHistoryInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public PasswordHistoryInvalidException(String errorCode, String message,
                                           HttpStatus status) {
        super(errorCode, message, status);
    }

    public PasswordHistoryInvalidException(Throwable cause, String errorCode, String message,
                                           HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
