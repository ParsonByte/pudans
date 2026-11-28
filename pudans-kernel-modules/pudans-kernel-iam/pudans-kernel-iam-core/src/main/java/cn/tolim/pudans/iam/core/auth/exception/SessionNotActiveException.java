package cn.tolim.pudans.iam.core.auth.exception;

import cn.tolim.pudans.iam.infra.exception.PudansException;
import org.springframework.http.HttpStatus;

public class SessionNotActiveException extends PudansException {

    public SessionNotActiveException() {
        this("Global login session is not active");
    }

    public SessionNotActiveException(String message) {
        super("session_not_active", message, HttpStatus.UNAUTHORIZED);
    }
}