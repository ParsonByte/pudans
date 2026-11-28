package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when a WebAuthn credential does not exist for the user
public class WebAuthnCredentialNotFoundException extends WebAuthnException {

    public WebAuthnCredentialNotFoundException() {
        super("webauthn_credential_not_found", "WebAuthn credential was not found",
                HttpStatus.NOT_FOUND);
    }
}