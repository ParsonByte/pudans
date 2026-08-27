package cn.tolim.pudans.infra.exception;

import java.net.URI;
import org.springframework.web.server.ServerWebInputException;


public class EmailAlreadyTakenException extends ServerWebInputException {

    public static final URI TYPE = URI.create("https://pudans.tolim.cn/errors/email-already-taken");

    public EmailAlreadyTakenException(String reason) {
        super(reason);
        setType(TYPE);
    }
}
