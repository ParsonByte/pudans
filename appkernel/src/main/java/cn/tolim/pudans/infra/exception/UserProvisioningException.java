package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UserProvisioningException extends ResponseStatusException {

    public UserProvisioningException(String reason) {
        super(HttpStatus.BAD_REQUEST, reason);
    }

    public UserProvisioningException(String reason, Throwable cause) {
        super(HttpStatus.BAD_REQUEST, reason, cause);
    }
}
