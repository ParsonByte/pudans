package cn.tolim.pudans.core.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UserNotFoundException extends ResponseStatusException {

    public UserNotFoundException(String username) {
        super(HttpStatus.NOT_FOUND, "用户 " + username + " 不存在", null, null, new Object[] {username});
    }
}
