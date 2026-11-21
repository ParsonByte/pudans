package cn.tolim.pudans.security.authentication;

import org.springframework.security.core.AuthenticationException;

public class LoginRejectedException extends AuthenticationException {

    private final String reason;

    // 以拒绝原因构造异常，原因同时作为异常消息。
    public LoginRejectedException(String reason) {
        super(reason);
        this.reason = reason;
    }

    // 保留底层原因，便于排查登录被拒绝的根因。
    public LoginRejectedException(String reason, Throwable cause) {
        super(reason, cause);
        this.reason = reason;
    }

    // 返回登录被拒绝的业务原因，供上层区分处理。
    public String getReason() {
        return reason;
    }
}
