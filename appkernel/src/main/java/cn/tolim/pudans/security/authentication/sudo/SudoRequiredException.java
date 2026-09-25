package cn.tolim.pudans.security.authentication.sudo;

import cn.tolim.pudans.infra.exception.Exceptions;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;


public class SudoRequiredException extends ResponseStatusException {

    public SudoRequiredException(List<String> methods) {
        super(HttpStatus.FORBIDDEN, "Sudo confirmation required", null, "problemDetail.sudo.required", null);
        setType(URI.create(Exceptions.SUDO_REQUIRED_TYPE));
        getBody().setProperty("methods", methods);
    }
}
