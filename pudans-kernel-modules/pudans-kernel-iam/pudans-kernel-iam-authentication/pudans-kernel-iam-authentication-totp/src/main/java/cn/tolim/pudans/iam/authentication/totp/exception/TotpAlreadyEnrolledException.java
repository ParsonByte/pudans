package cn.tolim.pudans.iam.authentication.totp.exception;

import org.springframework.http.HttpStatus;

// Raised when a user already owns an active TOTP credential
public class TotpAlreadyEnrolledException extends TotpAuthenticationException {

    public TotpAlreadyEnrolledException() {
        super("totp_already_enrolled", "TOTP is already enrolled for this user",
                HttpStatus.CONFLICT);
    }
}