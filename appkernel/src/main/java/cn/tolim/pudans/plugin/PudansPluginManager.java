package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.infra.SystemVersionSupplier;
import cn.tolim.pudans.contract.plugin.PluginsRootGetter;
import cn.tolim.pudans.contract.plugin.SpringPlugin;
import cn.tolim.pudans.contract.plugin.SpringPluginManager;
import cn.tolim.pudans.contract.plugin.event.PluginStartedEvent;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.CompoundPluginLoader;
import org.pf4j.CompoundPluginRepository;
import org.pf4j.DefaultPluginManager;
import org.pf4j.DefaultPluginRepository;
import org.pf4j.DefaultPluginStatusProvider;
import org.pf4j.ExtensionFactory;
import org.pf4j.ExtensionFinder;
import org.pf4j.JarPluginLoader;
import org.pf4j.JarPluginRepository;
import org.pf4j.PluginDescriptorFinder;
import org.pf4j.PluginFactory;
import org.pf4j.PluginLoader;
import org.pf4j.PluginRepository;
import org.pf4j.PluginState;
import org.pf4j.PluginStateEvent;
import org.pf4j.PluginStateListener;
import org.pf4j.PluginStatusProvider;
import org.pf4j.PluginWrapper;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.data.util.Lazy;


@Slf4j
class PudansPluginManager extends DefaultPluginManager
        implements SpringPluginManager, InitializingBean, DisposableBean {

    private final ApplicationContext rootContext;
    private final PluginProperties pluginProperties;
    private final PluginsRootGetter pluginsRootGetter;
    private final SystemVersionSupplier systemVersionSupplier;
    private Lazy<ApplicationContext> sharedContext;

    public PudansPluginManager(
            ApplicationContext rootContext,
            PluginProperties pluginProperties,
            SystemVersionSupplier systemVersionSupplier,
            PluginsRootGetter pluginsRootGetter) {
        this.pluginProperties = pluginProperties;
        this.rootContext = rootContext;
        this.pluginsRootGetter = pluginsRootGetter;
        this.systemVersionSupplier = systemVersionSupplier;
    }

    @Override
    protected void initialize() {


    }

    @Override
    public void afterPropertiesSet() throws Exception {
        super.runtimeMode = pluginProperties.getRuntimeMode();
        this.sharedContext = Lazy.of(() -> SharedApplicationContextFactory.create(rootContext));
        setExactVersionAllowed(pluginProperties.isExactVersionAllowed());
        setSystemVersion(systemVersionSupplier.get().toStableVersion().toString());

        super.initialize();

        addPluginStateListener(new PluginStartedListener());
    }

    @Override
    protected ExtensionFactory createExtensionFactory() {
        return new SpringExtensionFactory(this);
    }

    @Override
    protected ExtensionFinder createExtensionFinder() {
        var finder = new SpringComponentsFinder(this);
        addPluginStateListener(finder);
        return finder;
    }

    @Override
    protected PluginFactory createPluginFactory() {
        var contextFactory = new DefaultPluginApplicationContextFactory(this);
        var pluginGetter = rootContext.getBean(PluginGetter.class);
        return new SpringPluginFactory(contextFactory, pluginGetter);
    }

    @Override
    protected PluginDescriptorFinder createPluginDescriptorFinder() {
        return new YamlPluginDescriptorFinder();
    }

    @Override
    protected PluginLoader createPluginLoader() {
        var compoundLoader = new CompoundPluginLoader();
        compoundLoader.add(new DevPluginLoader(this, this.pluginProperties), this::isDevelopment);
        compoundLoader.add(new JarPluginLoader(this));
        return compoundLoader;
    }

    @Override
    protected PluginStatusProvider createPluginStatusProvider() {
        if (PropertyPluginStatusProvider.isPropertySet(pluginProperties)) {
            return new PropertyPluginStatusProvider(pluginProperties);
        }

        return new DefaultPluginStatusProvider(pluginsRootGetter.get());
    }

    @Override
    protected PluginRepository createPluginRepository() {
        var developmentPluginRepository = new DefaultDevelopmentPluginRepository(getPluginsRoots());
        developmentPluginRepository.setFixedPaths(pluginProperties.getFixedPluginPath());
        return new CompoundPluginRepository()
                .add(developmentPluginRepository, this::isDevelopment)
                .add(new JarPluginRepository(getPluginsRoots()))
                .add(new DefaultPluginRepository(getPluginsRoots()));
    }

    @Override
    protected List<Path> createPluginsRoot() {
        return List.of(pluginsRootGetter.get());
    }

    @Override
    public void startPlugins() {
        throw new UnsupportedOperationException("The operation of starting all plugins is not supported.");
    }

    @Override
    public void stopPlugins() {


        var pluginsToStop = new ArrayList<>(startedPlugins());
        log.debug(
                "Stopping {} started plugin(s): {}",
                pluginsToStop.size(),
                pluginsToStop.stream().map(PluginWrapper::getPluginId).toList());
        for (var pw : pluginsToStop) {
            try {
                var pluginState = stopPlugin(pw.getPluginId());
                log.debug("Plugin '{}' stop result: {}.", pw.getPluginId(), pluginState);
            } catch (Exception e) {
                log.warn("Error stopping plugin '{}' during shutdown.", pw.getPluginId(), e);
            }
        }
    }

    @Override
    public void unloadPlugins() {


        var pluginsToUnload = new ArrayList<>(getResolvedPlugins());
        log.debug(
                "Unloading {} resolved plugin(s): {}",
                pluginsToUnload.size(),
                pluginsToUnload.stream().map(PluginWrapper::getPluginId).toList());
        for (var pw : pluginsToUnload) {
            try {
                var unloaded = unloadPlugin(pw.getPluginId());
                log.debug("Plugin '{}' unload result: {}.", pw.getPluginId(), unloaded);
            } catch (Exception e) {
                log.warn("Error unloading plugin '{}' during shutdown.", pw.getPluginId(), e);
            }
        }
    }

    @Override
    public void destroy() {
        log.info("Stopping and unloading all plugins before application context shutdown...");
        stopPlugins();
        unloadPlugins();
        log.info("All plugins stopped and unloaded.");
    }

    @Override
    public ApplicationContext getRootContext() {
        return rootContext;
    }

    @Override
    public ApplicationContext getSharedContext() {
        return sharedContext.get();
    }

    @Override
    public List<PluginWrapper> getDependents(String pluginId) {
        if (getPlugin(pluginId) == null) {
            return List.of();
        }

        var dependents = new ArrayList<PluginWrapper>();
        var stack = new Stack<String>();
        dependencyResolver.getDependents(pluginId).forEach(stack::push);
        while (!stack.isEmpty()) {
            var dependent = stack.pop();
            var pluginWrapper = getPlugin(dependent);
            if (pluginWrapper != null) {
                dependents.add(pluginWrapper);
                dependencyResolver.getDependents(dependent).forEach(stack::push);
            }
        }
        return dependents;
    }

    @Override
    public List<PluginWrapper> startedPlugins() {
        return List.copyOf(super.getStartedPlugins()).stream()

                .filter(p -> p.getPluginState().isStarted())
                .toList();
    }


    private static class PluginStartedListener implements PluginStateListener {

        @Override
        public void pluginStateChanged(PluginStateEvent event) {
            if (PluginState.STARTED.equals(event.getPluginState())) {
                var plugin = event.getPlugin().getPlugin();
                if (plugin instanceof SpringPlugin springPlugin) {
                    try {
                        springPlugin.getApplicationContext().publishEvent(new PluginStartedEvent(this));
                    } catch (Throwable t) {
                        var pluginId = event.getPlugin().getPluginId();
                        log.warn("Error while publishing plugin started event for plugin {}", pluginId, t);
                    }
                }
            }
        }
    }
}
