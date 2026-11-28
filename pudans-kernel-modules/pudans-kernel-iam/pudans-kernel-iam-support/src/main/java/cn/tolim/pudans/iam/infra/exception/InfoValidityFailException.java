package cn.tolim.pudans.iam.infra.exception;

import org.springframework.http.HttpStatus;

public class InfoValidityFailException extends PudansException {
    public InfoValidityFailException(String message) {
        super(message);
    }

    public InfoValidityFailException(String message, HttpStatus status) {
        super(message, status);
    }

    public InfoValidityFailException(String message, Throwable cause) {
        super(message, cause);
    }

    public InfoValidityFailException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public InfoValidityFailException(Throwable cause, String errorCode, String message,
                                     HttpStatus status) {
        super(cause, errorCode, message, status);
    }
}
