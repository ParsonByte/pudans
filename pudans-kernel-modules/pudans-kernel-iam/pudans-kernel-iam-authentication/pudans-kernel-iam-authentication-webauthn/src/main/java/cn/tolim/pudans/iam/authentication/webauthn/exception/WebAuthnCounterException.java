package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when the authenticator signature counter did not advance
public class WebAuthnCounterException extends WebAuthnException {

    public WebAuthnCounterException() {
        super("webauthn_counter_regression",
                "Authenticator signature counter did not advance", HttpStatus.BAD_REQUEST);
    }
}