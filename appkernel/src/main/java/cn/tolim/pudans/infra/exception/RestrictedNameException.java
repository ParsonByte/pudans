package cn.tolim.pudans.infra.exception;

import jakarta.validation.constraints.Null;
import org.jspecify.annotations.Nullable;
import org.springframework.web.server.ServerWebInputException;


public class RestrictedNameException extends ServerWebInputException {

    public RestrictedNameException() {
        super("The name is restricted");
    }

    public RestrictedNameException(
            String reason, @Nullable String messageDetailCode, @Null Object[] messageDetailArguments) {
        super(reason, null, null, messageDetailCode, messageDetailArguments);
    }
}
