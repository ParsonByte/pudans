package cn.tolim.pudans.iam.authentication.webauthn.exception;

import org.springframework.http.HttpStatus;

// Raised when an attestation format is not supported
public class WebAuthnUnsupportedAttestationException extends WebAuthnException {

    public WebAuthnUnsupportedAttestationException(String format) {
        super("webauthn_attestation_unsupported",
                "Attestation format is not supported: " + format,
                HttpStatus.BAD_REQUEST);
    }
}