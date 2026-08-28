package cn.tolim.pudans.infra.exception;

import java.net.URI;
import java.util.List;
import org.springframework.web.server.ServerWebInputException;


public class PluginDependentsNotDisabledException extends ServerWebInputException {

    public static final URI TYPE = URI.create("https://www.pudans.tolim.cn/probs/plugin-dependents-not-disabled");


    public PluginDependentsNotDisabledException(List<String> dependents) {
        super("Plugin dependents are not fully disabled, please disable them first.", null, null, null, new Object[] {
                dependents
        });
        setType(TYPE);
        getBody().setProperty("dependents", dependents);
    }
}
