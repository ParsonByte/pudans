package cn.tolim.pudans.iam.core.auth.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

public class AuthenticationFailedException extends PudansException {

    public AuthenticationFailedException() {
        this("Bad credentials");
    }

    public AuthenticationFailedException(String message) {
        super("authentication_failed", message, HttpStatus.UNAUTHORIZED);
    }
}