package cn.tolim.pudans.iam.authentication.totp.exception;

import org.springframework.http.HttpStatus;

// Raised when a TOTP credential does not exist for the current user
public class TotpCredentialNotFoundException extends TotpAuthenticationException {

    public TotpCredentialNotFoundException() {
        super("totp_credential_not_found", "TOTP credential was not found", HttpStatus.NOT_FOUND);
    }
}