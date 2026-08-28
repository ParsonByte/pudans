package cn.tolim.pudans.infra.exception;

import java.net.URI;
import java.util.List;
import org.springframework.web.server.ServerWebInputException;


public class PluginDependenciesNotEnabledException extends ServerWebInputException {

    public static final URI TYPE = URI.create("https://www.pudans.tolim.cn/probs/plugin-dependencies-not-enabled");


    public PluginDependenciesNotEnabledException(List<String> dependencies) {
        super("Plugin dependencies are not fully enabled, please enable them first.", null, null, null, new Object[] {
                dependencies
        });
        setType(TYPE);
        getBody().setProperty("dependencies", dependencies);
    }
}
