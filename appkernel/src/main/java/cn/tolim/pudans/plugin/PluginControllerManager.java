package cn.tolim.pudans.plugin;

import static org.springframework.core.ResolvableType.forClassWithGenerics;

import cn.tolim.pudans.contract.extension.ExtensionClient;
import cn.tolim.pudans.contract.extension.controller.Controller;
import cn.tolim.pudans.contract.extension.controller.ControllerBuilder;
import cn.tolim.pudans.contract.extension.controller.Reconciler;
import cn.tolim.pudans.plugin.event.SpringPluginStartedEvent;
import cn.tolim.pudans.plugin.event.SpringPluginStoppingEvent;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.event.EventListener;
import reactor.core.Disposable;

public class PluginControllerManager {

    private final ConcurrentHashMap<String, Controller> controllers;

    private final ExtensionClient client;

    public PluginControllerManager(ExtensionClient client) {
        this.client = client;
        controllers = new ConcurrentHashMap<>();
    }

    @EventListener
    public void onApplicationEvent(SpringPluginStartedEvent event) {
        event.getSpringPlugin()
                .getApplicationContext()
                .<Reconciler<Reconciler.Request>>getBeanProvider(
                        forClassWithGenerics(Reconciler.class, Reconciler.Request.class))
                .orderedStream()
                .forEach(this::start);
    }

    @EventListener
    public void onApplicationEvent(SpringPluginStoppingEvent event) throws Exception {
        controllers.values().forEach(Disposable::dispose);
        controllers.clear();
    }

    private void start(Reconciler<Reconciler.Request> reconciler) {
        var builder = new ControllerBuilder(reconciler, client);
        var controller = reconciler.setupWith(builder);
        controllers.put(reconciler.getClass().getName(), controller);
        controller.start();
    }
}
