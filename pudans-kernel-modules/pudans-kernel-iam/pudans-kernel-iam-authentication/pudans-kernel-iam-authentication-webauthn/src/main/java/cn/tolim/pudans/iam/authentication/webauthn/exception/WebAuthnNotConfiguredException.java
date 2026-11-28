package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when WebAuthn is disabled or the relying party is incomplete
public class WebAuthnNotConfiguredException extends WebAuthnException {

    public WebAuthnNotConfiguredException(String message) {
        super("webauthn_not_configured", message, HttpStatus.SERVICE_UNAVAILABLE);
    }
}