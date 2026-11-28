package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when a user reached the configured credential limit
public class WebAuthnCredentialLimitException extends WebAuthnException {

    public WebAuthnCredentialLimitException(int limit) {
        super("webauthn_credential_limit",
                "Maximum number of WebAuthn credentials reached: " + limit,
                HttpStatus.CONFLICT);
    }
}