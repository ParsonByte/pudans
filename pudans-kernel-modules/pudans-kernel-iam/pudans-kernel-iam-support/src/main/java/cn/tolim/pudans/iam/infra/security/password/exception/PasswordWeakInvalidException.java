package cn.tolim.pudans.iam.infra.security.password.exception;

import org.springframework.http.HttpStatus;

public class PasswordWeakInvalidException extends PasswordInvalidException {

    public PasswordWeakInvalidException(String message) {
        super(message);
    }

    public PasswordWeakInvalidException(String message, HttpStatus status) {
        super(message, status);
    }

    public PasswordWeakInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public PasswordWeakInvalidException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public PasswordWeakInvalidException(Throwable cause, String errorCode, String message,
                                        HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
