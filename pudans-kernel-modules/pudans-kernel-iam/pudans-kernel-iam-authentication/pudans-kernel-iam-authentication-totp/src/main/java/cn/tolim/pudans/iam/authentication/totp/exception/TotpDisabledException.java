package cn.tolim.pudans.iam.authentication.totp.exception;

import org.springframework.http.HttpStatus;

// Raised when TOTP authentication is disabled by configuration
public class TotpDisabledException extends TotpAuthenticationException {

    public TotpDisabledException() {
        super("totp_disabled", "TOTP authentication is disabled", HttpStatus.NOT_FOUND);
    }
}