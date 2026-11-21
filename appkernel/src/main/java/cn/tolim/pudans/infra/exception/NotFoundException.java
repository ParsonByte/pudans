package cn.tolim.pudans.infra.exception;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


public class NotFoundException extends ResponseStatusException {

    // 创建带可选原因的资源不存在异常。
    public NotFoundException(@Nullable String reason) {
        this(reason, null);
    }

    // 创建带根因的资源不存在异常。
    public NotFoundException(@Nullable String reason, @Nullable Throwable cause) {
        super(HttpStatus.NOT_FOUND, reason, cause);
    }

    // 从根因消息推导原因，保持调用方兼容。
    public NotFoundException(@Nullable Throwable cause) {
        this(cause == null ? "" : cause.getMessage(), cause);
    }

    // 附带问题详情码与参数，统一错误响应文案。
    public NotFoundException(String messageDetailCode, Object[] messageDetailArgs, String reason) {
        super(HttpStatus.NOT_FOUND, reason, null, messageDetailCode, messageDetailArgs);
    }
}
