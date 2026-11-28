package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when the same credential is registered twice
public class WebAuthnDuplicateCredentialException extends WebAuthnException {

    public WebAuthnDuplicateCredentialException() {
        super("webauthn_credential_duplicate", "WebAuthn credential is already registered",
                HttpStatus.CONFLICT);
    }
}