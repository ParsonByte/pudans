package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when the client origin is not allowed by configuration
public class WebAuthnOriginException extends WebAuthnException {

    public WebAuthnOriginException(String origin) {
        super("webauthn_origin_denied", "WebAuthn origin is not allowed: " + origin,
                HttpStatus.BAD_REQUEST);
    }
}