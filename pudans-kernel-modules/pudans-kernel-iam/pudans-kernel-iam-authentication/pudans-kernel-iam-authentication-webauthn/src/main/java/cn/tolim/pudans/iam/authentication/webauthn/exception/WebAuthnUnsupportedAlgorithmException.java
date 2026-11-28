package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when a COSE algorithm or key type is not supported
public class WebAuthnUnsupportedAlgorithmException extends WebAuthnException {

    public WebAuthnUnsupportedAlgorithmException(String message) {
        super("webauthn_algorithm_unsupported", message, HttpStatus.BAD_REQUEST);
    }

    public WebAuthnUnsupportedAlgorithmException(String message, Throwable cause) {
        super("webauthn_algorithm_unsupported", message, HttpStatus.BAD_REQUEST, cause);
    }
}