package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


public class AccessDeniedException extends ResponseStatusException {

    public AccessDeniedException() {
        this("Access to the resource is forbidden");
    }

    public AccessDeniedException(String reason) {
        this(reason, null, null);
    }

    public AccessDeniedException(String reason, String detailCode, Object[] detailArgs) {
        super(HttpStatus.FORBIDDEN, reason, null, detailCode, detailArgs);
    }
}
