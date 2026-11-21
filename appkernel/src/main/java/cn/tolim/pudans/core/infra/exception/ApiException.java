package cn.tolim.pudans.core.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

// 带稳定机器可读码的 ResponseStatusException；该码会写入 RFC 7807 响应体，客户端无需解析人类可读文本。
public class ApiException extends ResponseStatusException {

    private final String code;

    public ApiException(HttpStatus status, String code, String reason) {
        super(status, reason);
        this.code = code;
    }

    public ApiException(HttpStatus status, String code, String reason, Throwable cause) {
        super(status, reason, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
