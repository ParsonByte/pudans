package cn.tolim.pudans.plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.pf4j.PluginStatusProvider;
import org.springframework.util.ObjectUtils;


public class PropertyPluginStatusProvider implements PluginStatusProvider {

    private final List<String> enabledPlugins;
    private final List<String> disabledPlugins;

    public PropertyPluginStatusProvider(PluginProperties pluginProperties) {
        this.enabledPlugins = pluginProperties.getEnabledPlugins() != null
                ? Arrays.asList(pluginProperties.getEnabledPlugins())
                : new ArrayList<>();
        this.disabledPlugins = pluginProperties.getDisabledPlugins() != null
                ? Arrays.asList(pluginProperties.getDisabledPlugins())
                : new ArrayList<>();
    }

    public static boolean isPropertySet(PluginProperties pluginProperties) {
        return !ObjectUtils.isEmpty(pluginProperties.getEnabledPlugins())
                && !ObjectUtils.isEmpty(pluginProperties.getDisabledPlugins());
    }

    @Override
    public boolean isPluginDisabled(String pluginId) {
        if (disabledPlugins.contains(pluginId)) {
            return true;
        }
        return !enabledPlugins.isEmpty() && !enabledPlugins.contains(pluginId);
    }

    @Override
    public void disablePlugin(String pluginId) {
        if (isPluginDisabled(pluginId)) {
            return;
        }
        disabledPlugins.add(pluginId);
        enabledPlugins.remove(pluginId);
    }

    @Override
    public void enablePlugin(String pluginId) {
        if (!isPluginDisabled(pluginId)) {
            return;
        }
        enabledPlugins.add(pluginId);
        disabledPlugins.remove(pluginId);
    }
}
