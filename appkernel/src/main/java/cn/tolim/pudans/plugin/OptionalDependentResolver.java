package cn.tolim.pudans.plugin;

import java.util.ArrayList;
import java.util.List;
import org.pf4j.PluginDependency;
import org.pf4j.PluginDescriptor;
import org.pf4j.util.DirectedGraph;


public class OptionalDependentResolver {
    private final DirectedGraph<String> dependentsGraph;
    private final List<PluginDescriptor> plugins;

    public OptionalDependentResolver(List<PluginDescriptor> startedPlugins) {
        this.plugins = startedPlugins;
        this.dependentsGraph = new DirectedGraph<>();
        resolve();
    }

    private void resolve() {

        for (PluginDescriptor plugin : plugins) {
            addPlugin(plugin);
        }
    }

    public List<String> getOptionalDependents(String pluginId) {
        return new ArrayList<>(dependentsGraph.getNeighbors(pluginId));
    }

    private void addPlugin(PluginDescriptor descriptor) {
        String pluginId = descriptor.getPluginId();
        List<PluginDependency> dependencies = descriptor.getDependencies();
        if (dependencies.isEmpty()) {
            dependentsGraph.addVertex(pluginId);
        } else {
            boolean edgeAdded = false;
            for (PluginDependency dependency : dependencies) {
                if (dependency.isOptional()) {
                    edgeAdded = true;
                    dependentsGraph.addEdge(dependency.getPluginId(), pluginId);
                }
            }


            if (!edgeAdded) {
                dependentsGraph.addVertex(pluginId);
            }
        }
    }
}
