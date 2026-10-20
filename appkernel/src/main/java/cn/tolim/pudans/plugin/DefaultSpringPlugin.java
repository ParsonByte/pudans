package cn.tolim.pudans.plugin;

import cn.tolim.pudans.contract.plugin.PluginContext;
import cn.tolim.pudans.contract.plugin.SpringPlugin;
import cn.tolim.pudans.plugin.event.SpringPluginStartedEvent;
import cn.tolim.pudans.plugin.event.SpringPluginStartingEvent;
import cn.tolim.pudans.plugin.event.SpringPluginStoppingEvent;
import org.jspecify.annotations.Nullable;
import org.pf4j.Plugin;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;


class DefaultSpringPlugin extends Plugin implements SpringPlugin {

    private final PluginApplicationContextFactory contextFactory;
    private final PluginContext pluginContext;
    private @Nullable ApplicationContext context;
    private @Nullable Plugin delegate;

    public DefaultSpringPlugin(PluginApplicationContextFactory contextFactory, PluginContext pluginContext) {
        this.contextFactory = contextFactory;
        this.pluginContext = pluginContext;
    }

    @Override
    public void start() {
        log.info("Preparing starting plugin {}", pluginContext.getName());
        var pluginId = pluginContext.getName();
        var previous = Thread.currentThread().getContextClassLoader();
        try {

            this.context = contextFactory.create(pluginId);
            Thread.currentThread().setContextClassLoader(this.context.getClassLoader());
            log.info("Application context {} for plugin {} is created", this.context, pluginId);

            var pluginOpt = context.getBeanProvider(Plugin.class).stream().findFirst();
            log.info("Before publishing plugin starting event for plugin {}", pluginId);
            context.publishEvent(new SpringPluginStartingEvent(this, this));
            log.info("After publishing plugin starting event for plugin {}", pluginId);
            if (pluginOpt.isPresent()) {
                this.delegate = pluginOpt.get();
                log.info("Starting {} for plugin {}", this.delegate, pluginId);
                this.delegate.start();
                log.info("Started {} for plugin {}", this.delegate, pluginId);
            }
            log.info("Before publishing plugin started event for plugin {}", pluginId);
            context.publishEvent(new SpringPluginStartedEvent(this, this));
            log.info("After publishing plugin started event for plugin {}", pluginId);
        } catch (Throwable t) {

            log.error("Cleaning up plugin resources for plugin {} due to not being able to start plugin.", pluginId);
            this.stop();

            throw t;
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
    }

    @Override
    public void stop() {
        var previous = Thread.currentThread().getContextClassLoader();
        try {
            if (context != null) {
                Thread.currentThread().setContextClassLoader(context.getClassLoader());
                log.info("Before publishing plugin stopping event for plugin {}", pluginContext.getName());
                context.publishEvent(new SpringPluginStoppingEvent(this, this));
                log.info("After publishing plugin stopping event for plugin {}", pluginContext.getName());
            }
            if (this.delegate != null) {
                log.info("Stopping {} for plugin {}", this.delegate, pluginContext.getName());
                this.delegate.stop();
                log.info("Stopped {} for plugin {}", this.delegate, pluginContext.getName());
            }
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
            if (context instanceof ConfigurableApplicationContext configurableContext) {
                log.info("Closing plugin context for plugin {}", pluginContext.getName());
                configurableContext.close();
                log.info("Closed plugin context for plugin {}", pluginContext.getName());
            }

            log.info("Reset plugin context for plugin {}", pluginContext.getName());
            context = null;
        }
    }

    @Override
    public void delete() {
        if (delegate != null) {
            delegate.delete();
        }
        this.delegate = null;
    }

    @Override
    public ApplicationContext getApplicationContext() {
        if (context == null) {
            throw new IllegalStateException("""
                    Plugin has not been started or has already been stopped; \
                    ApplicationContext is not available.
                    """);
        }
        return context;
    }

    @Override
    public PluginContext getPluginContext() {
        return pluginContext;
    }
}
