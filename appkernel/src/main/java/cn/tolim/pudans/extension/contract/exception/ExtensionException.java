package cn.tolim.pudans.extension.contract.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;


public class ExtensionException extends ResponseStatusException {

    // 以内部服务器错误和原因创建异常。
    public ExtensionException(String reason) {
        this(reason, null);
    }

    // 携带根因创建内部错误异常。
    public ExtensionException(String reason, Throwable cause) {
        this(HttpStatus.INTERNAL_SERVER_ERROR, reason, cause, null, new Object[] {reason});
    }

    // 允许子类指定状态码与消息参数。
    protected ExtensionException(
            HttpStatusCode status,
            String reason,
            Throwable cause,
            String messageDetailCode,
            Object[] messageDetailArguments) {
        super(status, reason, cause, messageDetailCode, messageDetailArguments);
    }
}
