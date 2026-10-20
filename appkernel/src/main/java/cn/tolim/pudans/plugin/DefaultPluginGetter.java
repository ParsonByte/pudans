package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.core.extension.Plugin;
import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.infra.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;


@Component
@RequiredArgsConstructor
public class DefaultPluginGetter implements PluginGetter {
    private final ExtensionClient client;

    @Override
    public Plugin getPlugin(String name) {
        if (StringUtils.isBlank(name)) {
            throw new IllegalArgumentException("Plugin name must not be blank");
        }
        return client.fetch(Plugin.class, name).orElseThrow(() -> new NotFoundException("Plugin not found"));
    }
}
