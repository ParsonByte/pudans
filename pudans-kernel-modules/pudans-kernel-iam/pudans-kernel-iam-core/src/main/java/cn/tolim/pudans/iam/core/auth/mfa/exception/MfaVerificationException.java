package cn.tolim.pudans.iam.core.auth.mfa.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

// Raised when the second factor does not verify against the pending challenge
public class MfaVerificationException extends PudansException {

    public MfaVerificationException() {
        this("The second factor is invalid");
    }

    public MfaVerificationException(String message) {
        super("mfa_verification_failed", message, HttpStatus.UNAUTHORIZED);
    }
}