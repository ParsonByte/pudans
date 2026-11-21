package cn.tolim.pudans.infra.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class UserNotFoundException extends ResponseStatusException {

    // 将用户名写入问题详情，便于客户端提示。
    public UserNotFoundException(String username) {
        super(HttpStatus.NOT_FOUND, "用户 " + username + " 不存在", null, null, new Object[] {username});
    }
}
