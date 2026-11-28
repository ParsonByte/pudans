package cn.tolim.pudans.iam.infra.security.password.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

public class PasswordInvalidException extends PudansException {

    public static final String ERROR_CODE = "password_invalid_error";

    public PasswordInvalidException(String message) {
        super(ERROR_CODE, message, HttpStatus.BAD_REQUEST);
    }

    public PasswordInvalidException(String message, HttpStatus status) {
        super(ERROR_CODE, message, status);
    }

    public PasswordInvalidException(String message, Throwable cause) {
        super(cause, ERROR_CODE, message, HttpStatus.BAD_REQUEST);
    }

    public PasswordInvalidException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public PasswordInvalidException(Throwable cause, String errorCode, String message,
                                    HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
