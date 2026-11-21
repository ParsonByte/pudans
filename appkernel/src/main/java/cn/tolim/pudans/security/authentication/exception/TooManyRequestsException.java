package cn.tolim.pudans.security.authentication.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.AuthenticationException;


public class TooManyRequestsException extends AuthenticationException {

    // 统一限流提示文案，避免调用方各自拼装消息
    public TooManyRequestsException(@Nullable Throwable throwable) {
        super("请求过于频繁，请稍后重试。", throwable);
    }
}
