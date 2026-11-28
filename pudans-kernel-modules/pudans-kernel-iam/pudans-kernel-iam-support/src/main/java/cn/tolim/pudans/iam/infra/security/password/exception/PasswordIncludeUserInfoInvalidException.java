package cn.tolim.pudans.iam.infra.security.password.exception;

import org.springframework.http.HttpStatus;

public class PasswordIncludeUserInfoInvalidException extends PasswordInvalidException {

    public PasswordIncludeUserInfoInvalidException(String message) {
        super(message);
    }

    public PasswordIncludeUserInfoInvalidException(String message, HttpStatus status) {
        super(message, status);
    }

    public PasswordIncludeUserInfoInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public PasswordIncludeUserInfoInvalidException(String errorCode, String message,
                                                   HttpStatus status) {
        super(errorCode, message, status);
    }

    public PasswordIncludeUserInfoInvalidException(Throwable cause, String errorCode,
                                                   String message, HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
