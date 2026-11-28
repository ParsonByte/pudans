package cn.tolim.pudans.iam.infra.security.password.exception;

import org.springframework.http.HttpStatus;

public class PasswordIllegalSequenceInvalidException extends PasswordInvalidException {

    public PasswordIllegalSequenceInvalidException(String message) {
        super(message);
    }

    public PasswordIllegalSequenceInvalidException(String message, HttpStatus status) {
        super(message, status);
    }

    public PasswordIllegalSequenceInvalidException(String message, Throwable cause) {
        super(message, cause);
    }

    public PasswordIllegalSequenceInvalidException(String errorCode, String message,
                                                   HttpStatus status) {
        super(errorCode, message, status);
    }

    public PasswordIllegalSequenceInvalidException(Throwable cause, String errorCode,
                                                   String message, HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
