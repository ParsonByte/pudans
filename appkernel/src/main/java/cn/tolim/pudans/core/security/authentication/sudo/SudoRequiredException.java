package cn.tolim.pudans.core.security.authentication.sudo;

import cn.tolim.pudans.core.infra.exception.Exceptions;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


public class SudoRequiredException extends ResponseStatusException {

    public SudoRequiredException(List<String> methods) {
        super(HttpStatus.FORBIDDEN, "需要安全确认。");
        setType(URI.create(Exceptions.SUDO_REQUIRED_TYPE));
        getBody().setProperty("methods", methods);
    }
}
