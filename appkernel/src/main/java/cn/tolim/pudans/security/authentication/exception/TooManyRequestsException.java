package cn.tolim.pudans.security.authentication.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.AuthenticationException;


public class TooManyRequestsException extends AuthenticationException {

    public TooManyRequestsException(@Nullable Throwable throwable) {
        super("Too many requests.", throwable);
    }
}
