package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when WebAuthn client data or authenticator data is malformed or invalid
public class WebAuthnVerificationException extends WebAuthnException {

    public WebAuthnVerificationException(String message) {
        super("webauthn_verification_failed", message, HttpStatus.BAD_REQUEST);
    }

    public WebAuthnVerificationException(String message, Throwable cause) {
        super("webauthn_verification_failed", message, HttpStatus.BAD_REQUEST, cause);
    }
}