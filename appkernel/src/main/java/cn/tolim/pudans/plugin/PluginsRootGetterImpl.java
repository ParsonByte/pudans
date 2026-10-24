package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.plugin.PluginsRootGetter;
import cn.tolim.pudans.infra.properties.PudansProperties;
import java.nio.file.Path;
import org.springframework.stereotype.Component;


@Component
public class PluginsRootGetterImpl implements PluginsRootGetter {

    private final PudansProperties pudansProperties;

    public PluginsRootGetterImpl(PudansProperties pudansProperties) {
        this.pudansProperties = pudansProperties;
    }

    @Override
    public Path get() {
        return pudansProperties.getWorkDir().resolve("plugins");
    }
}
