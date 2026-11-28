package cn.tolim.pudans.iam.authentication.totp.exception;

import org.springframework.http.HttpStatus;

// Raised when a TOTP code does not match the expected value
public class TotpCodeInvalidException extends TotpAuthenticationException {

    public TotpCodeInvalidException() {
        super("totp_code_invalid", "TOTP code is invalid", HttpStatus.BAD_REQUEST);
    }
}