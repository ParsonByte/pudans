package cn.tolim.pudans.iam.authentication.webauthn.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

// Base exception for WebAuthn failures
public class WebAuthnException extends PudansException {

    public WebAuthnException(String errorCode, String message, HttpStatus status) {
        super(errorCode, message, status);
    }

    public WebAuthnException(String errorCode, String message, HttpStatus status,
                             Throwable cause) {
        super(cause, errorCode, message, status);
    }
}