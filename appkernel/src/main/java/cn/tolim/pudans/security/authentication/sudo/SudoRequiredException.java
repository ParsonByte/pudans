package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.infra.exception.Exceptions;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


public class SudoRequiredException extends ResponseStatusException {

    // 携带可用验证方式返回需要安全确认的响应
    public SudoRequiredException(List<String> methods) {
        super(HttpStatus.FORBIDDEN, "需要安全确认。");
        setType(URI.create(Exceptions.SUDO_REQUIRED_TYPE));
        getBody().setProperty("methods", methods);
    }
}
