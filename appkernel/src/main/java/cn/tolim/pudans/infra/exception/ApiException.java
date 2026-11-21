package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 带稳定机器可读码的 ResponseStatusException；该码会写入 RFC 7807 响应体，客户端无需解析人类可读文本。
public class ApiException extends ResponseStatusException {

    private final String code;

    // 创建携带机器可读错误码的接口异常。
    public ApiException(HttpStatus status, String code, String reason) {
        super(status, reason);
        this.code = code;
    }

    // 创建带根因的错误，便于日志定位问题。
    public ApiException(HttpStatus status, String code, String reason, Throwable cause) {
        super(status, reason, cause);
        this.code = code;
    }

    // 返回写入问题详情的稳定错误码。
    public String getCode() {
        return code;
    }
}
