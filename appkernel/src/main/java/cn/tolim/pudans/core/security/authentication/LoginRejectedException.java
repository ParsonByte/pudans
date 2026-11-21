package cn.tolim.pudans.core.security.authentication;

import org.springframework.security.core.AuthenticationException;

public class LoginRejectedException extends AuthenticationException {

    private final String reason;

    public LoginRejectedException(String reason) {
        super(reason);
        this.reason = reason;
    }

    public LoginRejectedException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }

    public String getReason() {
        return reason;
    }
}
