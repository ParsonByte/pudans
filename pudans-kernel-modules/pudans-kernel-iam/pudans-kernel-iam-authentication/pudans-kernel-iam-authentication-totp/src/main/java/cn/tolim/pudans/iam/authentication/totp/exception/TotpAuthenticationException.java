package cn.tolim.pudans.iam.authentication.totp.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

// Base exception for TOTP authentication failures
public class TotpAuthenticationException extends PudansException {

    public TotpAuthenticationException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public TotpAuthenticationException(String errorCode, String message, HttpStatus status,
                                       Throwable cause) {
        super(cause, errorCode, message, status);
    }
}