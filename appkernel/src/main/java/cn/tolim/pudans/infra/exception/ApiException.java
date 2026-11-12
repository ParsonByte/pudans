package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * ResponseStatusException with a stable machine-readable code. The code is
 * copied into the RFC 7807 body so clients never have to parse human text.
 */
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
