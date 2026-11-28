package cn.tolim.pudans.iam.authentication.totp.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

// Raised when a user has no active TOTP credential
public class TotpNotEnrolledException extends TotpAuthenticationException {

    public TotpNotEnrolledException() {
        this(HttpStatus.BAD_REQUEST);
    }

    public TotpNotEnrolledException(HttpStatusCode status) {
        super("totp_not_enrolled", "TOTP is not enrolled for this user",
                HttpStatus.valueOf(status.value()));
    }
}