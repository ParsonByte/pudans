package cn.tolim.pudans.security.authentication.exception;

import org.springframework.security.core.AuthenticationException;

public class TwoFactorAuthException extends AuthenticationException {

    // 保留原始原因，便于上层记录双因素失败细节
    public TwoFactorAuthException(String msg, Throwable cause) {
        super(msg, cause);
    }

    // 仅携带提示消息，由调用方给出具体失败原因
    public TwoFactorAuthException(String msg) {
        super(msg);
    }
}
