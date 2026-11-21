package cn.tolim.pudans.core.security.authentication.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.AuthenticationException;


public class TooManyRequestsException extends AuthenticationException {

    public TooManyRequestsException(@Nullable Throwable throwable) {
        super("请求过于频繁，请稍后重试。", throwable);
    }
}
