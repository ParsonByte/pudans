package cn.tolim.pudans.infra.exception;

import jakarta.validation.constraints.Null;
import org.jspecify.annotations.Nullable;
import org.springframework.web.server.ServerWebInputException;


public class UnsatisfiedAttributeValueException extends ServerWebInputException {

    public UnsatisfiedAttributeValueException(String reason) {
        super(reason);
    }

    public UnsatisfiedAttributeValueException(
            String reason, @Nullable String messageDetailCode, @Null Object[] messageDetailArguments) {
        super(reason, null, null, messageDetailCode, messageDetailArguments);
    }
}
