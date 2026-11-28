package cn.tolim.pudans.iam.authentication.totp.exception;

import org.springframework.http.HttpStatus;

// Raised when a TOTP secret cannot be encrypted or decrypted
public class TotpSecretUnavailableException extends TotpAuthenticationException {

    public TotpSecretUnavailableException(Throwable cause) {
        super("totp_secret_unavailable", "TOTP secret protection is unavailable",
                HttpStatus.INTERNAL_SERVER_ERROR, cause);
    }
}