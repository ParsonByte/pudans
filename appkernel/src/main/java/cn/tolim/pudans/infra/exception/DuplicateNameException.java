package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class DuplicateNameException extends ResponseStatusException {

    // 使用默认重复名称原因创建异常。
    public DuplicateNameException() {
        this("检测到重复名称。");
    }

    // 使用调用方提供的原因创建异常。
    public DuplicateNameException(String reason) {
        this(reason, null);
    }

    // 保留根因，便于定位并发创建冲突。
    public DuplicateNameException(String reason, Throwable cause) {
        this(reason, cause, null, null);
    }

    // 附带问题详情码与参数，统一错误响应文案。
    public DuplicateNameException(
            String reason, Throwable cause, String messageDetailCode, Object[] messageDetailArguments) {
        super(HttpStatus.BAD_REQUEST, reason, cause, messageDetailCode, messageDetailArguments);
    }
}
