package cn.tolim.pudans.infra.exception;

import java.net.URI;
import org.springframework.web.server.ServerErrorException;


public class PluginRuntimeIncompatibleException extends ServerErrorException {

    private static final URI TYPE = URI.create("https://www.pudans.tolim.cn/probs/plugin-runtime-incompatible");

    public PluginRuntimeIncompatibleException(Throwable cause) {
        super("Incompatible plugin detected.", cause);
        setType(TYPE);
    }
}
