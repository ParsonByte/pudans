package cn.tolim.pudans.iam.core.security.util;

import org.springframework.security.authentication.event.*;


public final class SecurityUtils {
    // Anonymous user
    public static final String ANONYMOUS_USER = "anonymousUser";

    private SecurityUtils() {
    }

    // Get error information
    // Parameter event: AbstractAuthenticationFailureEvent
    // Returns: String
    public static String getFailureMessage(AbstractAuthenticationFailureEvent event) {
        String message = "未知错误";
        if (event instanceof AuthenticationFailureBadCredentialsEvent) {
            message = "登录密码错误";
        }
        if (event instanceof AuthenticationFailureCredentialsExpiredEvent) {
            message = "账户密码过期";
        }
        if (event instanceof AuthenticationFailureDisabledEvent) {
            message = "账户已禁用";
        }
        if (event instanceof AuthenticationFailureExpiredEvent) {
            message = "帐户已过期";
        }
        if (event instanceof AuthenticationFailureLockedEvent) {
            message = "账户已锁定";
        }
        if (event instanceof AuthenticationFailureProxyUntrustedEvent) {
            message = "代理不受信任";
        }
        if (event instanceof AuthenticationFailureProviderNotFoundEvent) {
            message = "提供商配置错误";
        }
        if (event instanceof AuthenticationFailureServiceExceptionEvent) {
            message = "发生内部异常";
        }
        return message;
    }

}
