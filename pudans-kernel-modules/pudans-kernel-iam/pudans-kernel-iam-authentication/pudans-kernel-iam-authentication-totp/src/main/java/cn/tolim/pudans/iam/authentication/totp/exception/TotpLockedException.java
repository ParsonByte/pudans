package cn.tolim.pudans.iam.authentication.totp.exception;

import org.springframework.http.HttpStatus;

// Raised when a TOTP credential is temporarily locked after repeated failures
public class TotpLockedException extends TotpAuthenticationException {

    public TotpLockedException() {
        super("totp_locked", "TOTP verification is temporarily locked", HttpStatus.LOCKED);
    }
}