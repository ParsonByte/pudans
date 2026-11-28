package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when a WebAuthn challenge is missing, expired, reused or mismatched
public class WebAuthnChallengeException extends WebAuthnException {

    public WebAuthnChallengeException(String message) {
        super("webauthn_challenge_invalid", message, HttpStatus.BAD_REQUEST);
    }
}