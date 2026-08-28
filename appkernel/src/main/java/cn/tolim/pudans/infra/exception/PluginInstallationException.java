package cn.tolim.pudans.infra.exception;

import jakarta.validation.constraints.Null;
import org.jspecify.annotations.Nullable;
import org.springframework.web.server.ServerWebInputException;


public class PluginInstallationException extends ServerWebInputException {

    public PluginInstallationException(
            String reason, @Nullable String messageDetailCode, @Null Object[] messageDetailArguments) {
        super(reason, null, null, messageDetailCode, messageDetailArguments);
    }
}
