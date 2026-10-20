package cn.tolim.pudans.plugin;

import static org.springframework.util.ResourceUtils.CLASSPATH_URL_PREFIX;

import cn.tolim.pudans.contract.core.endpoint.WebSocketEndpoint;
import cn.tolim.pudans.contract.extension.ReactiveExtensionClient;
import cn.tolim.pudans.contract.plugin.SpringPlugin;
import cn.tolim.pudans.contract.plugin.SpringPluginManager;
import cn.tolim.pudans.infra.properties.PudansProperties;
import cn.tolim.pudans.plugin.endpoint.WebSocketEndpointManager;
import cn.tolim.pudans.plugin.event.PudansPluginBeforeStopEvent;
import cn.tolim.pudans.plugin.event.PudansPluginStartedEvent;
import cn.tolim.pudans.plugin.event.PudansPluginStoppedEvent;
import cn.tolim.pudans.plugin.event.SpringPluginStartedEvent;
import cn.tolim.pudans.plugin.event.SpringPluginStoppedEvent;
import cn.tolim.pudans.plugin.event.SpringPluginStoppingEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.PluginRuntimeException;
import org.springframework.beans.factory.support.DefaultBeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.env.PropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Controller;
import org.springframework.util.StopWatch;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.Exceptions;

@Slf4j
public class DefaultPluginApplicationContextFactory implements PluginApplicationContextFactory {

    private final SpringPluginManager pluginManager;

    public DefaultPluginApplicationContextFactory(SpringPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }


    @Override
    public ApplicationContext create(String pluginId) {
        log.debug("Preparing to create application context for plugin {}", pluginId);

        var sw = new StopWatch("CreateApplicationContextFor" + pluginId);
        sw.start("Create");

        var pluginWrapper = pluginManager.getPlugin(pluginId);
        var classLoader = pluginWrapper.getPluginClassLoader();


        var beanFactory = new DefaultListableBeanFactory();
        beanFactory.setBeanClassLoader(classLoader);

        var context = new PluginApplicationContext(pluginId, pluginManager, beanFactory);
        context.setBeanNameGenerator(DefaultBeanNameGenerator.INSTANCE);
        context.registerShutdownHook();
        context.setParent(pluginManager.getSharedContext());

        var resourceLoader = new DefaultResourceLoader(classLoader);
        context.setResourceLoader(resourceLoader);
        sw.stop();

        sw.start("LoadPropertySources");
        var mutablePropertySources = context.getEnvironment().getPropertySources();

        resolvePropertySources(pluginId, resourceLoader).forEach(mutablePropertySources::addLast);
        sw.stop();

        sw.start("RegisterBeans");
        beanFactory.registerSingleton("pluginWrapper", pluginWrapper);
        context.registerBean(AggregatedRouterFunction.class);

        if (pluginWrapper.getPlugin() instanceof SpringPlugin springPlugin) {
            beanFactory.registerSingleton("pluginContext", springPlugin.getPluginContext());
        }

        var rootContext = pluginManager.getRootContext();
        rootContext.getBeanProvider(ReactiveExtensionClient.class).ifUnique(client -> {
            context.registerBean("reactiveSettingFetcher", DefaultReactiveSettingFetcher.class);
            context.registerBean("settingFetcher", DefaultSettingFetcher.class);
        });

        rootContext.getBeanProvider(PluginRequestMappingHandlerMapping.class).ifAvailable(handlerMapping -> {
            var handlerMappingManager = new PluginHandlerMappingManager(pluginId, handlerMapping);
            beanFactory.registerSingleton("pluginHandlerMappingManager", handlerMappingManager);
        });

        context.registerBean(PluginControllerManager.class);
        beanFactory.registerSingleton("springPluginStoppedEventAdapter", new SpringPluginStoppedEventAdapter(pluginId));
        beanFactory.registerSingleton("pudansPluginEventBridge", new PudansPluginEventBridge());

        rootContext
                .getBeanProvider(WebSocketEndpointManager.class)
                .ifUnique(manager -> beanFactory.registerSingleton(
                        "pluginWebSocketEndpointManager", new PluginWebSocketEndpointManager(manager)));

        rootContext.getBeanProvider(PluginRouterFunctionRegistry.class).ifUnique(registry -> {
            var pluginRouterFunctionManager = new PluginRouterFunctionManager(pluginId, registry);
            beanFactory.registerSingleton("pluginRouterFunctionManager", pluginRouterFunctionManager);
        });

        sw.stop();

        sw.start("LoadComponents");
        var classNames = pluginManager.getExtensionClassNames(pluginId);
        classNames.stream()
                .map(className -> {
                    try {
                        return classLoader.loadClass(className);
                    } catch (ClassNotFoundException e) {
                        throw new PluginRuntimeException(String.format("""
                                Failed to load class %s for plugin %s.\
                                """, className, pluginId), e);
                    }
                })
                .forEach(clazzName -> context.registerBean(clazzName));
        sw.stop();
        log.debug("Created application context for plugin {}", pluginId);

        log.debug("Refreshing application context for plugin {}", pluginId);
        sw.start("Refresh");


        var previous = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            context.refresh();
        } finally {

            Thread.currentThread().setContextClassLoader(previous);
        }
        sw.stop();
        log.debug("Refreshed application context for plugin {}", pluginId);
        if (log.isDebugEnabled()) {
            log.debug("\n{}", sw.prettyPrint(TimeUnit.MILLISECONDS));
        }
        return context;
    }

    private List<PropertySource<?>> resolvePropertySources(String pluginId, ResourceLoader resourceLoader) {
        var pudansProperties = pluginManager
                .getRootContext()
                .getBeanProvider(PudansProperties.class)
                .getIfAvailable();
        if (pudansProperties == null) {
            return List.of();
        }

        var propertySourceLoader = new YamlPropertySourceLoader();
        var propertySources = new ArrayList<PropertySource<?>>();
        var configsPath = pudansProperties.getWorkDir().resolve("plugins").resolve("configs");

        Stream.of(configsPath.resolve(pluginId + ".yaml"), configsPath.resolve(pluginId + ".yml"))
                .map(path -> resourceLoader.getResource(path.toUri().toString()))
                .forEach(resource -> {
                    var sources = loadPropertySources("user-defined-config", resource, propertySourceLoader);
                    propertySources.addAll(sources);
                });


        Stream.of(CLASSPATH_URL_PREFIX + "/config.yaml", CLASSPATH_URL_PREFIX + "/config.yml")
                .map(resourceLoader::getResource)
                .forEach(resource -> {
                    var sources = loadPropertySources("default-config", resource, propertySourceLoader);
                    propertySources.addAll(sources);
                });
        return propertySources;
    }

    private List<PropertySource<?>> loadPropertySources(
            String propertySourceName, Resource resource, PropertySourceLoader propertySourceLoader) {
        if (log.isDebugEnabled()) {
            log.debug("Loading property sources from {}", resource);
        }
        if (!resource.exists()) {
            return List.of();
        }
        try {
            return propertySourceLoader.load(propertySourceName, resource);
        } catch (IOException e) {
            throw Exceptions.propagate(e);
        }
    }

    private static class PluginWebSocketEndpointManager {

        private final WebSocketEndpointManager manager;

        private List<WebSocketEndpoint> endpoints;

        private PluginWebSocketEndpointManager(WebSocketEndpointManager manager) {
            this.manager = manager;
        }

        @EventListener
        public void onApplicationEvent(ContextRefreshedEvent event) {
            var context = event.getApplicationContext();
            this.endpoints = context.getBeanProvider(WebSocketEndpoint.class)
                    .orderedStream()
                    .toList();
            manager.register(this.endpoints);
        }

        @EventListener
        public void onApplicationEvent(ContextClosedEvent ignored) {
            manager.unregister(this.endpoints);
        }
    }

    private static class PluginRouterFunctionManager {

        private final String pluginId;

        private final PluginRouterFunctionRegistry routerFunctionRegistry;

        private Collection<RouterFunction<ServerResponse>> routerFunctions;

        private PluginRouterFunctionManager(
                String pluginId, PluginRouterFunctionRegistry routerFunctionRegistry) {
            this.pluginId = pluginId;
            this.routerFunctionRegistry = routerFunctionRegistry;
        }

        @EventListener
        public void onApplicationEvent(ContextClosedEvent ignored) {
            if (routerFunctions != null) {
                routerFunctionRegistry.unregister(routerFunctions);
            }
        }

        @EventListener
        public void onApplicationEvent(ContextRefreshedEvent event) {
            var routerFunctions = event.getApplicationContext()
                    .<RouterFunction<ServerResponse>>getBeanProvider(
                            ResolvableType.forClassWithGenerics(RouterFunction.class, ServerResponse.class))
                    .orderedStream()
                    .toList();
            routerFunctionRegistry.register(pluginId, routerFunctions);
            this.routerFunctions = routerFunctions;
        }
    }

    private static class PluginHandlerMappingManager {
        private final String pluginId;

        private final PluginRequestMappingHandlerMapping handlerMapping;

        private PluginHandlerMappingManager(String pluginId, PluginRequestMappingHandlerMapping handlerMapping) {
            this.pluginId = pluginId;
            this.handlerMapping = handlerMapping;
        }

        @EventListener
        public void onApplicationEvent(ContextRefreshedEvent event) {
            var context = event.getApplicationContext();
            context.getBeansWithAnnotation(Controller.class)
                    .values()
                    .forEach(controller -> handlerMapping.registerHandlerMethods(this.pluginId, controller));
        }

        @EventListener
        public void onApplicationEvent(ContextClosedEvent ignored) {
            handlerMapping.unregister(this.pluginId);
        }
    }

    private class SpringPluginStoppedEventAdapter implements ApplicationListener<ContextClosedEvent> {

        private final String pluginId;

        private SpringPluginStoppedEventAdapter(String pluginId) {
            this.pluginId = pluginId;
        }

        @Override
        public void onApplicationEvent(ContextClosedEvent event) {
            var plugin = pluginManager.getPlugin(pluginId).getPlugin();
            if (plugin instanceof SpringPlugin springPlugin) {
                event.getApplicationContext().publishEvent(new SpringPluginStoppedEvent(this, springPlugin));
            }
        }
    }

    private class PudansPluginEventBridge {

        @EventListener
        public void onApplicationEvent(SpringPluginStartedEvent event) {
            var pluginContext = event.getSpringPlugin().getPluginContext();
            var pluginWrapper = pluginManager.getPlugin(pluginContext.getName());

            pluginManager.getRootContext().publishEvent(new PudansPluginStartedEvent(this, pluginWrapper));
        }

        @EventListener
        public void onApplicationEvent(SpringPluginStoppingEvent event) {
            var pluginContext = event.getSpringPlugin().getPluginContext();
            var pluginWrapper = pluginManager.getPlugin(pluginContext.getName());
            pluginManager.getRootContext().publishEvent(new PudansPluginBeforeStopEvent(this, pluginWrapper));
        }

        @EventListener
        public void onApplicationEvent(SpringPluginStoppedEvent event) {
            var pluginContext = event.getSpringPlugin().getPluginContext();
            var pluginWrapper = pluginManager.getPlugin(pluginContext.getName());
            pluginManager.getRootContext().publishEvent(new PudansPluginStoppedEvent(this, pluginWrapper));
        }
    }
}
