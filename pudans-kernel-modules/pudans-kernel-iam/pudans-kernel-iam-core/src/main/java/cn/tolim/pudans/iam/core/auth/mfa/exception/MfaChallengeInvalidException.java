package cn.tolim.pudans.iam.core.auth.mfa.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

// Raised when an MFA challenge is missing, expired, already consumed or does not match the request
public class MfaChallengeInvalidException extends PudansException {

    public MfaChallengeInvalidException() {
        this("MFA challenge is invalid or expired");
    }

    public MfaChallengeInvalidException(String message) {
        super("mfa_challenge_invalid", message, HttpStatus.UNAUTHORIZED);
    }
}